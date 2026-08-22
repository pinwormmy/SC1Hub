package com.sc1hub.chat.controller;

import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.assistant.dto.AssistantChatResponseDTO;
import com.sc1hub.assistant.dto.AssistantRelatedPostDTO;
import com.sc1hub.assistant.service.AssistantRateLimiter;
import com.sc1hub.assistant.service.AssistantService;
import com.sc1hub.chat.config.ChatProperties;
import com.sc1hub.chat.dto.ChatAiRequestDTO;
import com.sc1hub.chat.dto.ChatAiResponseDTO;
import com.sc1hub.chat.dto.ChatMessageDTO;
import com.sc1hub.chat.dto.ChatPollResponseDTO;
import com.sc1hub.chat.dto.ChatPostRequestDTO;
import com.sc1hub.chat.dto.ChatPostResponseDTO;
import com.sc1hub.chat.dto.ChatSelfDTO;
import com.sc1hub.chat.service.ChatModerationService;
import com.sc1hub.chat.service.ChatRejectedException;
import com.sc1hub.chat.service.ChatRoomService;
import com.sc1hub.common.util.IpService;
import com.sc1hub.member.dto.MemberDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/chat")
@Slf4j
public class ChatController {

    private static final String CHAT_MAINTENANCE_MESSAGE = "채팅 기능은 현재 점검중입니다. 잠시 후 다시 이용해주세요.";

    private final ChatRoomService chatRoomService;
    private final ChatModerationService moderationService;
    private final ChatProperties chatProperties;
    private final AssistantService assistantService;
    private final AssistantProperties assistantProperties;
    private final AssistantRateLimiter assistantRateLimiter;

    public ChatController(ChatRoomService chatRoomService,
                          ChatModerationService moderationService,
                          ChatProperties chatProperties,
                          AssistantService assistantService,
                          AssistantProperties assistantProperties,
                          AssistantRateLimiter assistantRateLimiter) {
        this.chatRoomService = chatRoomService;
        this.moderationService = moderationService;
        this.chatProperties = chatProperties;
        this.assistantService = assistantService;
        this.assistantProperties = assistantProperties;
        this.assistantRateLimiter = assistantRateLimiter;
    }

    @GetMapping(value = "/messages", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChatPollResponseDTO> messages(@RequestParam(name = "afterSeq", defaultValue = "0") long afterSeq,
                                                        HttpServletRequest request,
                                                        HttpSession session) {
        if (!chatProperties.isEnabled()) {
            ChatPollResponseDTO response = new ChatPollResponseDTO();
            response.setError(CHAT_MAINTENANCE_MESSAGE);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }

        ChatPollResponseDTO response = chatRoomService.poll(afterSeq);
        if (afterSeq <= 0) {
            response.setSelf(buildSelf(request, session));
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/messages", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChatPostResponseDTO> post(@RequestBody(required = false) ChatPostRequestDTO request,
                                                    HttpServletRequest httpRequest,
                                                    HttpSession session) {
        ChatPostResponseDTO response = new ChatPostResponseDTO();
        if (!chatProperties.isEnabled()) {
            response.setError(CHAT_MAINTENANCE_MESSAGE);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }

        MemberDTO member = getMember(session);
        String ip = resolveClientIp(httpRequest);
        String content = request == null ? null : request.getContent();

        try {
            ChatMessageDTO message = chatRoomService.postUserMessage(member, session, ip, content);
            response.setMessage(message);
            response.setLastSeq(message.getId());
            return ResponseEntity.ok(response);
        } catch (ChatRejectedException e) {
            response.setError(e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(response);
        } catch (Exception e) {
            log.error("채팅 메시지 등록 중 오류 발생", e);
            response.setError("메시지 전송 중 오류가 발생했습니다.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping(value = "/ai", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChatAiResponseDTO> ai(@RequestBody(required = false) ChatAiRequestDTO request,
                                                HttpServletRequest httpRequest,
                                                HttpSession session) {
        ChatAiResponseDTO response = new ChatAiResponseDTO();
        if (!chatProperties.isEnabled() || !assistantProperties.isEnabled()) {
            response.setError("AI 채팅 기능은 현재 점검중입니다. 잠시 후 다시 이용해주세요.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }

        String question = request == null ? null : request.getQuestion();
        if (!StringUtils.hasText(question)) {
            response.setError("질문을 입력해주세요.");
            return ResponseEntity.badRequest().body(response);
        }
        question = question.trim();

        MemberDTO member = getMember(session);
        if (assistantProperties.isRequireLogin() && member == null) {
            response.setError("로그인 후 이용할 수 있습니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String ip = resolveClientIp(httpRequest);
        String memberId = member == null ? null : member.getId();
        String restriction = moderationService.checkRestricted(memberId, ip);
        if (restriction != null) {
            response.setError(restriction);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }
        String blockedWord = moderationService.findBlockedWord(question);
        if (blockedWord != null) {
            response.setError("금지어가 포함되어 있습니다.");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            // Quota check happens before anything is posted, so a denied request stays private.
            String sessionId = session == null ? null : session.getId();
            AssistantRateLimiter.RateLimitResult rateLimit = assistantRateLimiter.tryConsume(member, ip, sessionId);
            response.setUsageText(buildUsageText(member, rateLimit));
            if (!rateLimit.isAllowed()) {
                response.setError(response.getUsageText() + " - 일일 한도 초과");
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
            }

            String nickname = chatRoomService.resolveNickname(member, session);
            String role = chatRoomService.resolveRole(member);
            ChatMessageDTO questionMessage = chatRoomService.postPublicAiQuestion(memberId, nickname, role, ip, question);
            response.setQuestionMessage(questionMessage);

            AssistantChatResponseDTO result = assistantService.chat(question, member);
            String answerText;
            if (result == null || StringUtils.hasText(result.getError()) || !StringUtils.hasText(result.getAnswer())) {
                answerText = "죄송합니다. 답변 생성 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
                response.setError(result == null ? "AI 응답 생성에 실패했습니다." : result.getError());
            } else {
                answerText = buildAnswerText(result);
            }

            ChatMessageDTO answerMessage = chatRoomService.postAiMessage(answerText);
            response.setAnswerMessage(answerMessage);
            response.setLastSeq(answerMessage == null ? 0 : answerMessage.getId());
            return ResponseEntity.ok(response);
        } catch (ChatRejectedException e) {
            response.setError(e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(response);
        } catch (Exception e) {
            log.error("AI 채팅 응답 생성 중 오류 발생", e);
            response.setError("AI 응답 생성 중 오류가 발생했습니다.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private ChatSelfDTO buildSelf(HttpServletRequest request, HttpSession session) {
        MemberDTO member = getMember(session);
        ChatSelfDTO self = new ChatSelfDTO();
        self.setNickname(chatRoomService.resolveNickname(member, session));
        self.setRole(chatRoomService.resolveRole(member));
        self.setPollIntervalMillis(chatProperties.getPollIntervalMillis());
        self.setHiddenPollIntervalMillis(chatProperties.getHiddenPollIntervalMillis());
        self.setMaxMessageLength(chatProperties.getMaxMessageLength());

        String memberId = member == null ? null : member.getId();
        String restriction = moderationService.checkRestricted(memberId, resolveClientIp(request));
        if (restriction != null) {
            self.setMuted(true);
            self.setMutedText(restriction);
        }
        return self;
    }

    private String buildAnswerText(AssistantChatResponseDTO result) {
        StringBuilder text = new StringBuilder(result.getAnswer().trim());
        if (result.getRelatedPosts() != null && !result.getRelatedPosts().isEmpty()) {
            AssistantRelatedPostDTO related = result.getRelatedPosts().get(0);
            if (related != null && StringUtils.hasText(related.getTitle())) {
                String url = StringUtils.hasText(related.getUrl())
                        ? related.getUrl()
                        : "/boards/" + related.getBoardTitle() + "/readPost?postNum=" + related.getPostNum();
                text.append("\n관련: ").append(related.getTitle().trim()).append(" ").append(url);
            }
        }
        return text.toString();
    }

    private String buildUsageText(MemberDTO member, AssistantRateLimiter.RateLimitResult rateLimit) {
        String label;
        if (member == null) {
            label = "비로그인 사용자";
        } else if (member.getGrade() == assistantProperties.getAdminGrade()
                || (assistantProperties.getAdminId() != null && assistantProperties.getAdminId().equals(member.getId()))) {
            label = "관리자";
        } else {
            label = "로그인 사용자";
        }
        if (rateLimit == null) {
            return label + "의 AI사용";
        }
        if (rateLimit.isUnlimited()) {
            return String.format("%s의 AI사용 (%d/∞)", label, rateLimit.getUsed());
        }
        return String.format("%s의 AI사용 (%d/%d)", label, rateLimit.getUsed(), rateLimit.getLimit());
    }

    private static MemberDTO getMember(HttpSession session) {
        return session == null ? null : (MemberDTO) session.getAttribute("member");
    }

    static String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ip = IpService.getRemoteIP(request);
        if (ip == null) {
            return null;
        }
        // Behind the hosting proxy X-FORWARDED-FOR can be "client, proxy1, ..." —
        // keep only the client address so IP sanctions never target the proxy.
        int comma = ip.indexOf(',');
        if (comma >= 0) {
            ip = ip.substring(0, comma);
        }
        return ip.trim();
    }
}
