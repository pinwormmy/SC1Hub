package com.sc1hub.common.security;

import com.sc1hub.chat.service.ChatModerationService;
import com.sc1hub.common.util.IpService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 거부된 요청(속도 제한·비회원 쓰기 시도·로그인 실패·봇 필드·중복 내용 등)을 IP·회원별로 누적해
 * 임계치를 넘으면 자동으로 제재(IP 차단 또는 회원 뮤트)를 건다. 제재는 기존 채팅 제재 테이블에
 * 저장되어 재시작 후에도 유지되며, 같은 대상이 24시간 안에 다시 걸리면 기간이 늘어난다.
 * 프록시 주소·사설 주소는 절대 차단하지 않는다.
 */
@Component
@Slf4j
public class OffenderTracker {

    static final int IP_STRIKE_LIMIT = 30;
    static final int MEMBER_STRIKE_LIMIT = 20;
    static final long STRIKE_WINDOW_MILLIS = 10 * 60 * 1000L;
    static final int FIRST_BAN_MINUTES = 60;
    static final int REPEAT_BAN_MINUTES = 24 * 60;
    static final int ATTACK_BAN_MINUTES = 24 * 60;
    static final int SUSPICIOUS_CONTENT_WEIGHT = 5;
    private static final long REPEAT_WINDOW_MILLIS = 24 * 60 * 60 * 1000L;
    private static final int MAX_BAN_HISTORY = 5_000;
    private static final String GLOBAL_STRIKES_KEY = "strikes:all";

    private final ChatModerationService moderationService;
    private final WriteRateLimiter counters;
    private final Map<String, Long> lastAutoBan = new LinkedHashMap<>();
    private final AtomicInteger autoBanCount = new AtomicInteger();

    public OffenderTracker(ChatModerationService moderationService, WriteRateLimiter counters) {
        this.moderationService = moderationService;
        this.counters = counters;
    }

    /**
     * 거부 1회를 기록한다. {@code ipTrusted}가 false(프록시 헤더에서 클라이언트를 못 얻은 직접 접속 등)면
     * IP 쪽은 세지 않는다.
     */
    public void strike(String ip, boolean ipTrusted, String memberId, String nickname, String reason) {
        strike(ip, ipTrusted, memberId, nickname, reason, 1);
    }

    /** 가중치만큼 거부를 누적한다(의심 내용은 5, 일반 거부는 1). */
    public void strike(String ip, boolean ipTrusted, String memberId, String nickname, String reason, int weight) {
        for (int i = 0; i < Math.max(1, weight); i++) {
            counters.allow(GLOBAL_STRIKES_KEY, Integer.MAX_VALUE, STRIKE_WINDOW_MILLIS);
            if (StringUtils.hasText(memberId)
                    && !counters.allow("strike-member:" + memberId, MEMBER_STRIKE_LIMIT, STRIKE_WINDOW_MILLIS)) {
                autoBan(ChatModerationService.TYPE_MUTE, memberId, null, nickname, reason, FIRST_BAN_MINUTES);
            }
            if (ipTrusted && IpService.isPublicAddress(ip)
                    && !counters.allow("strike-ip:" + ip, IP_STRIKE_LIMIT, STRIKE_WINDOW_MILLIS)) {
                autoBan(ChatModerationService.TYPE_BLOCK_IP, null, ip, nickname, reason, FIRST_BAN_MINUTES);
            }
        }
    }

    /**
     * 내용 검사에서 공격 의도가 확인된 경우. HIGH 는 즉시 24시간 제재(회원이면 뮤트, 아니면 IP 차단),
     * MEDIUM 은 가중 스트라이크로 누적한다.
     */
    public void attackDetected(String ip, boolean ipTrusted, String memberId, String nickname,
                               AttackContentDetector.Verdict verdict) {
        if (verdict == null || !verdict.isAttack()) {
            return;
        }
        String reason = "공격 패턴 감지: " + verdict.rule();
        counters.allow(GLOBAL_STRIKES_KEY, Integer.MAX_VALUE, STRIKE_WINDOW_MILLIS);
        if (!verdict.isHigh()) {
            strike(ip, ipTrusted, memberId, nickname, reason, SUSPICIOUS_CONTENT_WEIGHT);
            return;
        }
        if (StringUtils.hasText(memberId)) {
            autoBan(ChatModerationService.TYPE_MUTE, memberId, null, nickname, reason, ATTACK_BAN_MINUTES);
        } else if (ipTrusted && IpService.isPublicAddress(ip)) {
            autoBan(ChatModerationService.TYPE_BLOCK_IP, null, ip, nickname, reason, ATTACK_BAN_MINUTES);
        }
    }

    /** 최근 10분간 거부 횟수(관리자 상태 표시용). */
    public int recentStrikeCount() {
        return counters.count(GLOBAL_STRIKES_KEY);
    }

    public int autoBanCount() {
        return autoBanCount.get();
    }

    private synchronized void autoBan(String type, String memberId, String ip, String nickname, String reason,
                                      int baseMinutes) {
        if (moderationService.checkRestricted(memberId, ip) != null) {
            return; // 이미 제재 중
        }
        String key = type + ":" + (memberId != null ? memberId : ip);
        long now = System.currentTimeMillis();
        Long previous = lastAutoBan.get(key);
        int minutes = previous != null && now - previous < REPEAT_WINDOW_MILLIS
                ? Math.max(baseMinutes, REPEAT_BAN_MINUTES) : baseMinutes;
        String label = truncate(StringUtils.hasText(nickname) ? nickname : (memberId != null ? memberId : ip), 40);
        String detail = truncate("자동 차단: " + (reason == null ? "반복 거부" : reason), 200);
        try {
            moderationService.addSanction(type, memberId, ip, label, minutes, detail, "auto");
            lastAutoBan.remove(key);
            lastAutoBan.put(key, now);
            while (lastAutoBan.size() > MAX_BAN_HISTORY) {
                String oldest = lastAutoBan.keySet().iterator().next();
                lastAutoBan.remove(oldest);
            }
            autoBanCount.incrementAndGet();
            log.warn("자동 제재 적용: type={}, target={}, minutes={}, reason={}", type,
                    memberId != null ? memberId : ip, minutes, detail);
        } catch (Exception e) {
            log.error("자동 제재 저장 실패. type={}, target={}", type, memberId != null ? memberId : ip, e);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
