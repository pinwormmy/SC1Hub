package com.sc1hub.buildtimer.controller;

import com.sc1hub.buildtimer.dto.BuildTimerFeedbackDTO;
import com.sc1hub.buildtimer.dto.BuildTimerFeedbackRequestDTO;
import com.sc1hub.buildtimer.service.BuildTimerFeedbackService;
import com.sc1hub.common.security.AttackContentDetector;
import com.sc1hub.common.security.DuplicateContentGuard;
import com.sc1hub.common.security.OffenderTracker;
import com.sc1hub.common.util.IpService;
import com.sc1hub.member.dto.MemberDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 빌드 타이머 (beta) 사용자 피드백 공개 API.
 */
@RestController
@RequestMapping("/api/build-timer")
@Slf4j
public class BuildTimerFeedbackController {

    private static final int FEEDBACK_MAX_LINKS = 2;
    private static final int DUPLICATE_MIN_LENGTH = 5;
    private static final long DUPLICATE_WINDOW_MILLIS = 10 * 60_000L;
    private static final String ATTACK_CONTENT_MESSAGE = "허용되지 않는 내용(스크립트·주입 구문·과다 링크 등)이 포함되어 있습니다.";

    private final BuildTimerFeedbackService feedbackService;
    private final AttackContentDetector attackContentDetector;
    private final DuplicateContentGuard duplicateContentGuard;
    private final OffenderTracker offenderTracker;

    public BuildTimerFeedbackController(BuildTimerFeedbackService feedbackService,
                                        AttackContentDetector attackContentDetector,
                                        DuplicateContentGuard duplicateContentGuard,
                                        OffenderTracker offenderTracker) {
        this.feedbackService = feedbackService;
        this.attackContentDetector = attackContentDetector;
        this.duplicateContentGuard = duplicateContentGuard;
        this.offenderTracker = offenderTracker;
    }

    @PostMapping(value = "/feedback", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> submitFeedback(
            @RequestBody(required = false) BuildTimerFeedbackRequestDTO request,
            HttpServletRequest httpRequest,
            HttpSession session) {
        if (request == null) {
            return error(HttpStatus.BAD_REQUEST, "피드백 요청 형식을 확인해주세요.");
        }

        if (StringUtils.hasText(request.getWebsite())) {
            log.warn("빌드 타이머 피드백 허니팟 감지 - ip={}", resolveClientIp(httpRequest));
            Map<String, Object> decoy = new LinkedHashMap<>();
            decoy.put("success", true);
            decoy.put("message", "피드백이 접수되었습니다. 감사합니다!");
            return ResponseEntity.ok(decoy);
        }

        MemberDTO member = getMember(session);
        String memberId = member == null ? null : member.getId();
        String nickname = member == null ? null : member.getNickName();
        String ip = resolveClientIp(httpRequest);
        boolean ipTrusted = IpService.hasForwardedClient(httpRequest);

        AttackContentDetector.Verdict verdict = attackContentDetector.inspect(
                FEEDBACK_MAX_LINKS, request.getMessage(), request.getContact());
        if (verdict.isAttack()) {
            if (!isStaff(member)) {
                offenderTracker.attackDetected(ip, ipTrusted, memberId, nickname, verdict);
            }
            log.warn("공격 패턴 빌드 타이머 피드백 거부 - rule={}, severity={}, member={}, ip={}",
                    verdict.rule(), verdict.severity(), memberId, ip);
            return error(HttpStatus.BAD_REQUEST, ATTACK_CONTENT_MESSAGE);
        }

        String actorKey = memberId != null ? "m:" + memberId : (ipTrusted && ip != null ? "ip:" + ip : "anon");
        if (duplicateContentGuard.isDuplicate("build-timer-feedback", actorKey,
                request.getMessage(), DUPLICATE_MIN_LENGTH, DUPLICATE_WINDOW_MILLIS)) {
            return error(HttpStatus.TOO_MANY_REQUESTS, "같은 내용의 피드백이 방금 접수되었습니다. 잠시 후 다시 시도해주세요.");
        }

        try {
            BuildTimerFeedbackDTO saved = feedbackService.submitFeedback(
                    request, memberId, nickname, ip, httpRequest.getHeader("User-Agent"));
            log.info("빌드 타이머 피드백 접수 - id={}, category={}, buildId={}, messageLength={}",
                    saved.getId(), saved.getCategory(), saved.getBuildId(),
                    saved.getMessage() == null ? 0 : saved.getMessage().length());
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("id", saved.getId());
            response.put("message", "피드백이 접수되었습니다. 소중한 의견 감사합니다!");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("빌드 타이머 피드백 저장 중 오류 발생", e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "피드백 전송 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    private static MemberDTO getMember(HttpSession session) {
        return session == null ? null : (MemberDTO) session.getAttribute("member");
    }

    private static boolean isStaff(MemberDTO member) {
        return member != null && member.getGrade() == 3;
    }

    private static String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ip = IpService.getRemoteIP(request);
        if (ip == null) {
            return null;
        }
        int comma = ip.indexOf(',');
        if (comma >= 0) {
            ip = ip.substring(0, comma);
        }
        return ip.trim();
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }
}
