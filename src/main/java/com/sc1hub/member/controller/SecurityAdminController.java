package com.sc1hub.member.controller;

import com.sc1hub.chat.dto.ChatSanctionDTO;
import com.sc1hub.chat.service.ChatModerationService;
import com.sc1hub.common.security.OffenderTracker;
import com.sc1hub.common.security.SecuritySwitches;
import com.sc1hub.common.util.IpService;
import com.sc1hub.member.dto.MemberDTO;
import com.sc1hub.member.mapper.MemberMapper;
import com.sc1hub.member.service.LegacyPasswordMigration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 관리자 전용 보안 운영 API. {@code /api/admin/**} 는 AdminInterceptor 가 보호한다.
 * 쓰기 스위치(재배포 없이 즉시 반영), 제재(IP 차단·회원 뮤트) 관리, 자동 차단 현황, 레거시 비밀번호
 * 승격 현황을 제공한다. 비밀번호 값은 어떤 형태로도 반환하지 않는다.
 */
@RestController
@RequestMapping("/api/admin/security")
@Slf4j
public class SecurityAdminController {

    private static final int MAX_REASON_LENGTH = 200;
    private static final int MAX_LABEL_LENGTH = 40;

    private final MemberMapper memberMapper;
    private final LegacyPasswordMigration legacyPasswordMigration;
    private final SecuritySwitches switches;
    private final ChatModerationService moderationService;
    private final OffenderTracker offenderTracker;

    public SecurityAdminController(MemberMapper memberMapper, LegacyPasswordMigration legacyPasswordMigration,
                                   SecuritySwitches switches, ChatModerationService moderationService,
                                   OffenderTracker offenderTracker) {
        this.memberMapper = memberMapper;
        this.legacyPasswordMigration = legacyPasswordMigration;
        this.switches = switches;
        this.moderationService = moderationService;
        this.offenderTracker = offenderTracker;
    }

    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> status() {
        return buildStatus();
    }

    @PostMapping(value = "/public-writes", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> setPublicWrites(@RequestBody Map<String, Object> body, HttpSession session) {
        boolean enabled = parseEnabled(body);
        switches.setPublicWritesEnabled(enabled);
        log.warn("회원 쓰기 스위치 변경: enabled={}, by={}", enabled, adminId(session));
        return buildStatus();
    }

    @PostMapping(value = "/guest-writes", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> setGuestWrites(@RequestBody Map<String, Object> body, HttpSession session) {
        boolean enabled = parseEnabled(body);
        switches.setGuestWritesEnabled(enabled);
        log.warn("비회원 쓰기 스위치 변경: enabled={}, by={}", enabled, adminId(session));
        return buildStatus();
    }

    @GetMapping(value = "/sanctions", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> sanctions() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ChatSanctionDTO sanction : moderationService.getActiveSanctions()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", sanction.getId());
            row.put("type", sanction.getSanctionType());
            row.put("target", ChatModerationService.TYPE_MUTE.equals(sanction.getSanctionType())
                    ? sanction.getMemberId() : sanction.getIp());
            row.put("nickname", sanction.getNickname());
            row.put("reason", sanction.getReason());
            row.put("expiresAtText", sanction.getExpiresAtText());
            row.put("createdBy", sanction.getCreatedBy());
            rows.add(row);
        }
        return rows;
    }

    @PostMapping(value = "/sanctions", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> addSanction(@RequestBody Map<String, Object> body,
                                                           HttpSession session) {
        String type = text(body.get("type"));
        String target = text(body.get("target"));
        Integer minutes = parseMinutes(body.get("minutes"));
        String reason = truncate(text(body.get("reason")), MAX_REASON_LENGTH);
        if (!ChatModerationService.TYPE_MUTE.equals(type) && !ChatModerationService.TYPE_BLOCK_IP.equals(type)) {
            return error(HttpStatus.BAD_REQUEST, "제재 유형은 MUTE 또는 BLOCK_IP 이어야 합니다.");
        }
        if (target == null) {
            return error(HttpStatus.BAD_REQUEST, "대상(회원 ID 또는 IP)을 입력해주세요.");
        }
        String memberId = null;
        String ip = null;
        String label;
        if (ChatModerationService.TYPE_MUTE.equals(type)) {
            MemberDTO member = memberMapper.getMemberInfo(target);
            if (member == null) {
                return error(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다.");
            }
            if (member.getGrade() == 3) {
                return error(HttpStatus.BAD_REQUEST, "관리자 계정은 제재할 수 없습니다.");
            }
            memberId = member.getId();
            label = StringUtils.hasText(member.getNickName()) ? member.getNickName() : member.getId();
        } else {
            if (!IpService.isValidIp(target)) {
                return error(HttpStatus.BAD_REQUEST, "올바른 IP 주소가 아닙니다.");
            }
            if (!IpService.isPublicAddress(target)) {
                return error(HttpStatus.BAD_REQUEST, "사설·루프백 주소는 차단할 수 없습니다(프록시 자체를 막아 전체 이용자가 차단됩니다).");
            }
            ip = target;
            label = target;
        }
        ChatSanctionDTO sanction = moderationService.addSanction(type, memberId, ip, truncate(label, MAX_LABEL_LENGTH),
                minutes, reason, adminId(session));
        log.warn("관리자 제재 추가: type={}, target={}, minutes={}, by={}", type, target, minutes, adminId(session));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("id", sanction.getId());
        response.put("expiresAtText", sanction.getExpiresAtText());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping(value = "/sanctions/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> revokeSanction(@PathVariable("id") long id, HttpSession session) {
        moderationService.revokeSanction(id);
        log.warn("관리자 제재 해제: id={}, by={}", id, adminId(session));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        return response;
    }

    /** 프록시가 이 요청에 붙인 주소 정보를 그대로 보여 준다. IP 판정·차단 설정 검증용. */
    @GetMapping(value = "/request-echo", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> requestEcho(HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("remoteAddr", request.getRemoteAddr());
        body.put("forwardedFor", request.getHeader("X-Forwarded-For"));
        body.put("realIp", request.getHeader("X-Real-IP"));
        body.put("forwardedProto", request.getHeader("X-Forwarded-Proto"));
        body.put("resolvedClientIp", IpService.getRemoteIP(request));
        body.put("forwardedClientTrusted", IpService.hasForwardedClient(request));
        body.put("plainHttpViaProxy", IpService.isPlainHttpViaProxy(request));
        return body;
    }

    @PostMapping(value = "/legacy-passwords/migrate", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> migrateLegacyPasswords() {
        LegacyPasswordMigration.Summary summary = legacyPasswordMigration.run();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("legacyPasswordMigration", summary);
        body.put("legacyPasswordCount", memberMapper.countLegacyPasswordMembers());
        return body;
    }

    private Map<String, Object> buildStatus() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("publicWritesEnabled", switches.isPublicWritesEnabled());
        body.put("guestWritesEnabled", switches.isGuestWritesEnabled());
        body.put("publicWritesDefault", switches.isPublicWritesDefault());
        body.put("guestWritesDefault", switches.isGuestWritesDefault());
        List<ChatSanctionDTO> activeSanctions = moderationService.getActiveSanctions();
        body.put("activeSanctionCount", activeSanctions == null ? 0 : activeSanctions.size());
        body.put("recentRejections10Min", offenderTracker.recentStrikeCount());
        body.put("autoBansSinceStart", offenderTracker.autoBanCount());
        body.put("legacyPasswordCount", memberMapper.countLegacyPasswordMembers());
        body.put("legacyPasswordMigration", legacyPasswordMigration.getLastRun());
        return body;
    }

    private static boolean parseEnabled(Map<String, Object> body) {
        Object value = body == null ? null : body.get("enabled");
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static Integer parseMinutes(Object value) {
        if (value == null) {
            return null;
        }
        try {
            int minutes = value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value).trim());
            return minutes > 0 ? minutes : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String trimmed = String.valueOf(value).trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String adminId(HttpSession session) {
        Object member = session == null ? null : session.getAttribute("member");
        if (member instanceof MemberDTO memberDTO && StringUtils.hasText(memberDTO.getId())) {
            return memberDTO.getId();
        }
        return "admin";
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }
}
