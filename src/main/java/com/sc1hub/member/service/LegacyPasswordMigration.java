package com.sc1hub.member.service;

import com.sc1hub.member.dto.MemberDTO;
import com.sc1hub.member.mapper.MemberMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * 레거시(평문) 비밀번호 행을 서버 안에서 BCrypt 로 일괄 승격한다.
 *
 * <p>저장값이 평문인 행은 그 값 자체가 사용자가 입력해야 하는 비밀번호이므로, 그 값을 BCrypt 로 해시해
 * 제자리에 저장하면 로그인 검증({@code PasswordEncoder.matches})은 그대로 통과한다. 즉 손실 없는 변환이다.
 * 평문은 어디에도 기록·전송하지 않으며, 읽은 뒤 즉시 해시하고 버린다.
 *
 * <p>안전장치: (1) {@code $2} 접두사 행은 절대 건드리지 않는다, (2) 갱신은 "읽었을 때와 값이 같을 때만"
 * 적용하는 낙관적 UPDATE 라 동시 로그인 승격과 충돌하지 않는다, (3) 72바이트를 넘는 평문은 BCrypt 절단
 * 문제로 건너뛰고 로그로만 알린다, (4) 어떤 예외도 애플리케이션 기동을 막지 않는다.
 * 기동 시 한 번 실행되며 관리자 API 로 재실행할 수 있다. 모든 행이 승격되면 이후 실행은 no-op 이다.
 */
@Component
@Slf4j
public class LegacyPasswordMigration {

    private static final String BCRYPT_PREFIX = "$2";
    private static final int MAX_BCRYPT_BYTES = 72;

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabledOnStartup;
    private volatile Summary lastRun;

    public LegacyPasswordMigration(MemberMapper memberMapper, PasswordEncoder passwordEncoder,
                                   @Value("${sc1hub.security.legacy-password-migration-enabled:true}")
                                   boolean enabledOnStartup) {
        this.memberMapper = memberMapper;
        this.passwordEncoder = passwordEncoder;
        this.enabledOnStartup = enabledOnStartup;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrateOnStartup() {
        if (!enabledOnStartup) {
            log.info("레거시 비밀번호 일괄 승격은 설정으로 비활성화되어 있습니다.");
            return;
        }
        run();
    }

    /** 승격을 실행하고 요약을 돌려준다. 기동 실패로 이어지지 않도록 모든 예외를 삼키고 요약에 남긴다. */
    public synchronized Summary run() {
        int candidates = 0;
        int upgraded = 0;
        int skipped = 0;
        int failed = 0;
        String error = null;
        try {
            List<MemberDTO> legacyMembers = memberMapper.selectLegacyPasswordMembers();
            if (legacyMembers == null) {
                legacyMembers = Collections.emptyList();
            }
            candidates = legacyMembers.size();
            for (MemberDTO member : legacyMembers) {
                String stored = member.getPw();
                if (stored == null || stored.isEmpty() || stored.startsWith(BCRYPT_PREFIX)) {
                    skipped++;
                    continue;
                }
                if (stored.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_BYTES) {
                    // 72바이트 초과 평문은 BCrypt 가 뒷부분을 무시하므로 자동 승격하지 않는다.
                    skipped++;
                    log.warn("레거시 비밀번호가 {}바이트를 넘어 자동 승격을 건너뜁니다. memberId={}",
                            MAX_BCRYPT_BYTES, member.getId());
                    continue;
                }
                try {
                    String hash = passwordEncoder.encode(stored);
                    int updatedRows = memberMapper.updatePasswordIfUnchanged(member.getId(), stored, hash);
                    if (updatedRows == 1) {
                        upgraded++;
                    } else {
                        // 그 사이 로그인 승격 등으로 값이 바뀐 행. 다음 실행에서 다시 판단한다.
                        skipped++;
                        log.info("레거시 비밀번호가 읽은 뒤 바뀌어 건너뜁니다. memberId={}", member.getId());
                    }
                } catch (Exception e) {
                    failed++;
                    log.error("레거시 비밀번호 승격 실패. memberId={}", member.getId(), e);
                }
            }
        } catch (Exception e) {
            error = e.getClass().getSimpleName();
            log.error("레거시 비밀번호 일괄 승격을 수행하지 못했습니다.", e);
        }
        Summary summary = new Summary(Instant.now(), candidates, upgraded, skipped, failed, error);
        lastRun = summary;
        log.info("레거시 비밀번호 일괄 승격 결과: 대상 {}건, 승격 {}건, 건너뜀 {}건, 실패 {}건{}",
                candidates, upgraded, skipped, failed, error == null ? "" : ", 오류 " + error);
        return summary;
    }

    public Summary getLastRun() {
        return lastRun;
    }

    /** 실행 요약. 비밀번호 값은 어떤 형태로도 포함하지 않는다. */
    public record Summary(Instant ranAt, int candidates, int upgraded, int skipped, int failed, String error) {
    }
}
