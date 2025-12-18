package com.sc1hub.assistant;

import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.assistant.dto.AssistantChatRequestDTO;
import com.sc1hub.assistant.dto.AssistantChatResponseDTO;
import com.sc1hub.common.util.IpService;
import com.sc1hub.member.MemberDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/assistant")
@Slf4j
public class AssistantController {

    private final AssistantService assistantService;
    private final AssistantProperties assistantProperties;
    private final AssistantRateLimiter assistantRateLimiter;

    public AssistantController(AssistantService assistantService,
                               AssistantProperties assistantProperties,
                               AssistantRateLimiter assistantRateLimiter) {
        this.assistantService = assistantService;
        this.assistantProperties = assistantProperties;
        this.assistantRateLimiter = assistantRateLimiter;
    }

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AssistantChatResponseDTO> chat(@RequestBody(required = false) AssistantChatRequestDTO request,
                                                        HttpServletRequest httpRequest,
                                                        HttpSession session) {
        AssistantChatResponseDTO response = new AssistantChatResponseDTO();

        if (!assistantProperties.isEnabled()) {
            response.setError("AI 기능이 비활성화되어 있습니다.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }

        String message = request == null ? null : request.getMessage();
        if (!StringUtils.hasText(message)) {
            response.setError("질문을 입력해주세요.");
            return ResponseEntity.badRequest().body(response);
        }

        MemberDTO member = session == null ? null : (MemberDTO) session.getAttribute("member");
        if (assistantProperties.isRequireLogin() && member == null) {
            response.setError("로그인 후 이용할 수 있습니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        try {
            String ip = httpRequest == null ? null : IpService.getRemoteIP(httpRequest);
            String sessionId = session == null ? null : session.getId();
            AssistantRateLimiter.RateLimitResult rateLimit = assistantRateLimiter.tryConsume(member, ip, sessionId);

            String userTypeLabel = resolveUserTypeLabel(member);
            String usageText = buildUsageText(userTypeLabel, rateLimit);
            response.setUsageText(usageText);
            response.setUsageUsed(rateLimit.getUsed());
            response.setUsageLimit(rateLimit.isUnlimited() ? null : rateLimit.getLimit());
            response.setUsageUnlimited(rateLimit.isUnlimited());

            if (!rateLimit.isAllowed()) {
                response.setError(usageText + " - 일일 한도 초과");
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
            }

            AssistantChatResponseDTO result = assistantService.chat(message, member);
            if (StringUtils.hasText(result.getError())) {
                result.setUsageText(usageText);
                result.setUsageUsed(rateLimit.getUsed());
                result.setUsageLimit(rateLimit.isUnlimited() ? null : rateLimit.getLimit());
                result.setUsageUnlimited(rateLimit.isUnlimited());
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(result);
            }

            result.setUsageText(usageText);
            result.setUsageUsed(rateLimit.getUsed());
            result.setUsageLimit(rateLimit.isUnlimited() ? null : rateLimit.getLimit());
            result.setUsageUnlimited(rateLimit.isUnlimited());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("AI 응답 생성 중 오류 발생", e);
            response.setError("AI 응답 생성 중 오류가 발생했습니다.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private String resolveUserTypeLabel(MemberDTO member) {
        if (member == null) {
            return "비로그인 사용자";
        }
        if (member.getGrade() == assistantProperties.getAdminGrade()
                || (assistantProperties.getAdminId() != null && assistantProperties.getAdminId().equals(member.getId()))) {
            return "관리자";
        }
        return "로그인 사용자";
    }

    private static String buildUsageText(String userTypeLabel, AssistantRateLimiter.RateLimitResult rateLimit) {
        if (rateLimit == null) {
            return userTypeLabel + "의 AI사용";
        }
        if (rateLimit.isUnlimited()) {
            return String.format("%s의 AI사용 (%d/∞)", userTypeLabel, rateLimit.getUsed());
        }
        return String.format("%s의 AI사용 (%d/%d)", userTypeLabel, rateLimit.getUsed(), rateLimit.getLimit());
    }
}
