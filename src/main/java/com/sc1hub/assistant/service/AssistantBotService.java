package com.sc1hub.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.assistant.config.AssistantBotProperties;
import com.sc1hub.assistant.config.AssistantBotProperties.PersonaProperties;
import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.assistant.dto.AssistantBotDraftRequestDTO;
import com.sc1hub.assistant.dto.AssistantBotDraftResponseDTO;
import com.sc1hub.assistant.dto.AssistantBotAutoPublishStatusDTO;
import com.sc1hub.assistant.dto.AssistantBotHistoryDTO;
import com.sc1hub.assistant.dto.AssistantBotHistorySummaryDTO;
import com.sc1hub.assistant.dto.AssistantBotPublishResponseDTO;
import com.sc1hub.assistant.gemini.GeminiClient;
import com.sc1hub.assistant.gemini.GeminiException;
import com.sc1hub.assistant.mapper.AssistantBotMapper;
import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.board.dto.CommentDTO;
import com.sc1hub.board.mapper.BoardMapper;
import com.sc1hub.board.service.BoardService;
import com.sc1hub.chat.dto.ChatMessageDTO;
import com.sc1hub.chat.service.ChatRoomService;
import com.sc1hub.common.util.BlockedWordMatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AssistantBotService {

    private static final String MODE_POST = "post";
    private static final String MODE_COMMENT = "comment";
    private static final String MODE_CHAT = "chat";
    private static final int CHAT_BODY_MAX_CHARS = 200;
    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_SKIPPED = "skipped";
    private static final String STATUS_PUBLISHED = "published";
    private static final String OUTCOME_FAILED = "failed";
    private static final Pattern NON_TEXT_PATTERN = Pattern.compile("[^가-힣a-z0-9]");
    private static final Pattern TITLE_COMMENT_COUNT_PATTERN = Pattern.compile("\\s*\\(\\s*\\d+\\s*\\)\\s*$");
    private static final String TOPIC_LANE_STAR_CHAT = "스타수다";
    private static final String TOPIC_LANE_DAILY_LIFE = "일상글";
    private static final String TOPIC_LANE_LIGHT_CHAT = "잡담/뻘글";
    private static final String TOPIC_LANE_WHIRING = "밸런스징징";
    private static final String POST_STRATEGY_LINKED = "linked";
    private static final String POST_STRATEGY_FRESH = "fresh";
    private static final String AUTO_DRAFT_SKIPPED_AFTER_GENERATION = "draft_skipped_after_generation:";
    private static final String AUTO_DRAFT_BUDGET_EXCEEDED = "generate_call_budget_exceeded";
    private static final int LINKED_TITLE_KEYWORD_OVERLAP_LIMIT = 3;
    private static final int FRESH_TITLE_KEYWORD_OVERLAP_LIMIT = 3;

    private final AssistantBotProperties botProperties;
    private final AssistantProperties assistantProperties;
    private final BoardService boardService;
    private final BoardMapper boardMapper;
    private final AssistantBotMapper assistantBotMapper;
    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;
    private final ChatRoomService chatRoomService;
    private final Clock clock;
    private LocalDate generateCallBudgetDate;
    private int generateCallBudgetUsed;

    @Autowired
    public AssistantBotService(AssistantBotProperties botProperties,
                               AssistantProperties assistantProperties,
                               BoardService boardService,
                               BoardMapper boardMapper,
                               AssistantBotMapper assistantBotMapper,
                               GeminiClient geminiClient,
                               ObjectMapper objectMapper,
                               ChatRoomService chatRoomService) {
        this(botProperties, assistantProperties, boardService, boardMapper, assistantBotMapper, geminiClient, objectMapper,
                chatRoomService, Clock.systemDefaultZone());
    }

    AssistantBotService(AssistantBotProperties botProperties,
                               AssistantProperties assistantProperties,
                               BoardService boardService,
                               BoardMapper boardMapper,
                               AssistantBotMapper assistantBotMapper,
                               GeminiClient geminiClient,
                               ObjectMapper objectMapper,
                               ChatRoomService chatRoomService,
                               Clock clock) {
        this.botProperties = botProperties;
        this.assistantProperties = assistantProperties;
        this.boardService = boardService;
        this.boardMapper = boardMapper;
        this.assistantBotMapper = assistantBotMapper;
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
        this.chatRoomService = chatRoomService;
        this.clock = clock;
    }

    public AssistantBotDraftResponseDTO generateDraft(AssistantBotDraftRequestDTO request) {
        return generateDraftInternal(request, botProperties.getMaxGenerateAttempts(), false);
    }

    private AssistantBotDraftResponseDTO generateDraftInternal(AssistantBotDraftRequestDTO request,
                                                               int requestedMaxAttempts,
                                                               boolean autoPublish) {
        AssistantBotDraftResponseDTO response = new AssistantBotDraftResponseDTO();

        if (!botProperties.isEnabled()) {
            response.setError("봇 초안 생성 기능이 비활성화되어 있습니다.");
            return response;
        }

        PersonaProperties persona = resolvePersona(request == null ? null : request.getPersonaName());
        if (persona == null) {
            response.setError("사용 가능한 봇 페르소나를 찾지 못했습니다.");
            return response;
        }
        response.setPersonaName(persona.getName());

        String mode = normalizeMode(request == null ? null : request.getMode());
        if (!MODE_POST.equals(mode) && !MODE_COMMENT.equals(mode)) {
            response.setError("mode는 post 또는 comment 여야 합니다.");
            return response;
        }

        String boardTitle = resolveBoardTitle(request == null ? null : request.getBoardTitle(), persona);
        if (boardTitle == null) {
            response.setError("현재는 설정된 대상 게시판에서만 초안을 생성할 수 있습니다.");
            return response;
        }

        Integer targetPostNum = request == null ? null : request.getTargetPostNum();
        if (MODE_COMMENT.equals(mode) && (targetPostNum == null || targetPostNum <= 0)) {
            response.setError("댓글 모드에서는 targetPostNum이 필요합니다.");
            return response;
        }

        response.setBoardTitle(boardTitle);
        response.setMode(mode);
        response.setTargetPostNum(targetPostNum);

        try {
            int maxAttempts = Math.max(1, requestedMaxAttempts);
            boolean useRecentContext = shouldUseRecentContext(persona);

            List<BoardDTO> recentPosts = Collections.emptyList();
            List<CommentDTO> recentComments = Collections.emptyList();
            List<AssistantBotHistoryDTO> recentHistory = Collections.emptyList();
            if (useRecentContext) {
                int recentPostLimit = resolveLimit(request == null ? null : request.getRecentPostLimit(), botProperties.getRecentPostLimit());
                int recentCommentLimit = resolveLimit(request == null ? null : request.getRecentCommentLimit(), botProperties.getRecentCommentLimit());
                int recentHistoryLimit = Math.max(1, botProperties.getRecentHistoryLimit());
                recentPosts = safeList(boardMapper.selectRecentPostsForBot(boardTitle, recentPostLimit));
                recentComments = safeList(boardMapper.selectRecentCommentsForBot(boardTitle, targetPostNum, recentCommentLimit));
                recentHistory = safeList(
                        assistantBotMapper.selectRecentHistory(persona.getName(), boardTitle, recentHistoryLimit)
                );
            }

            BoardDTO targetPost = null;
            if (MODE_COMMENT.equals(mode) && useRecentContext) {
                targetPost = boardMapper.readPost(boardTitle, targetPostNum);
                if (targetPost == null) {
                    response.setError("대상 게시글을 찾지 못했습니다.");
                    return response;
                }
                if (isLatestCommentByPersona(recentComments, persona)) {
                    AssistantBotHistoryDTO skippedHistory = createSkippedCommentHistory(persona, boardTitle, targetPostNum,
                            "latest_comment_is_self");
                    assistantBotMapper.insertHistory(skippedHistory);

                    response.setHistoryId(skippedHistory.getId());
                    response.setStatus(STATUS_SKIPPED);
                    response.setRecentPostCount(recentPosts.size());
                    response.setRecentCommentCount(recentComments.size());
                    response.setRecentHistoryCount(recentHistory.size());
                    response.setResult(parseJson(skippedHistory.getRawJson()));
                    return response;
                }
            }

            CandidateDraft acceptedDraft = null;
            String retryFeedback = null;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                GenerateCallBudgetResult budgetResult = tryConsumeGenerateCallBudget();
                if (!budgetResult.isAllowed()) {
                    if (autoPublish) {
                        response.setStatus(STATUS_SKIPPED);
                        response.setResult(parseJson("{\"skip_reason\":\"" + AUTO_DRAFT_BUDGET_EXCEEDED + "\"}"));
                    } else {
                        response.setError(budgetResult.getMessage());
                    }
                    return response;
                }
                String prompt = buildPrompt(persona, mode, boardTitle, targetPost, recentPosts, recentComments, recentHistory, retryFeedback, attempt, maxAttempts);
                String rawJson = geminiClient.generateAnswer(
                        prompt,
                        Math.max(1, botProperties.getMaxOutputTokens()),
                        resolveModel(persona)
                );
                JsonNode result = parseJson(rawJson);
                CandidateDraft candidate = validateCandidate(persona, mode, result, recentPosts, recentComments, recentHistory);
                if (candidate.accepted) {
                    acceptedDraft = candidate;
                    response.setAttemptCount(attempt);
                    break;
                }
                retryFeedback = candidate.feedback;
                log.info("봇 초안 재시도. attempt={}, feedback={}", attempt, retryFeedback);
            }

            if (acceptedDraft == null) {
                response.setError(StringUtils.hasText(retryFeedback)
                        ? "초안 품질 기준을 만족하지 못했습니다: " + retryFeedback
                        : "초안 품질 기준을 만족하지 못했습니다.");
                return response;
            }

            AssistantBotHistoryDTO history = new AssistantBotHistoryDTO();
            history.setPersonaName(persona.getName());
            history.setBoardTitle(boardTitle);
            history.setGenerationMode(mode);
            history.setTargetPostNum(targetPostNum);
            history.setTopic(acceptedDraft.topic);
            history.setDraftTitle(acceptedDraft.title);
            history.setDraftBody(acceptedDraft.body == null ? "" : acceptedDraft.body);
            history.setRawJson(acceptedDraft.result.toString());
            history.setStatus(MODE_COMMENT.equals(mode) && !acceptedDraft.shouldReply ? STATUS_SKIPPED : STATUS_DRAFT);
            assistantBotMapper.insertHistory(history);

            response.setHistoryId(history.getId());
            response.setStatus(history.getStatus());
            response.setRecentPostCount(recentPosts.size());
            response.setRecentCommentCount(recentComments.size());
            response.setRecentHistoryCount(recentHistory.size());
            response.setResult(acceptedDraft.result);
            return response;
        } catch (GeminiException e) {
            log.error("봇 초안 Gemini 호출 실패", e);
            response.setError("Gemini 호출에 실패했습니다.");
            return response;
        } catch (Exception e) {
            log.error("봇 초안 생성 실패", e);
            response.setError("봇 초안 생성 중 오류가 발생했습니다.");
            return response;
        }
    }

    private synchronized GenerateCallBudgetResult tryConsumeGenerateCallBudget() {
        int dailyLimit = Math.max(0, botProperties.getDailyGenerateCallLimit());
        if (dailyLimit <= 0) {
            return GenerateCallBudgetResult.allowed();
        }

        LocalDate today = LocalDate.now(clock);
        if (!today.equals(generateCallBudgetDate)) {
            generateCallBudgetDate = today;
            generateCallBudgetUsed = 0;
        }
        if (generateCallBudgetUsed >= dailyLimit) {
            return GenerateCallBudgetResult.blocked("봇 AI 생성 일일 호출 한도(" + dailyLimit + "회)를 초과했습니다.");
        }
        generateCallBudgetUsed += 1;
        return GenerateCallBudgetResult.allowed();
    }

    public AssistantBotPublishResponseDTO publishDraft(long historyId) {
        AssistantBotPublishResponseDTO response = new AssistantBotPublishResponseDTO();
        response.setHistoryId(historyId);

        if (!botProperties.isEnabled()) {
            response.setError("봇 초안 생성 기능이 비활성화되어 있습니다.");
            return response;
        }

        try {
            AssistantBotHistoryDTO history = assistantBotMapper.selectHistoryById(historyId);
            if (history == null) {
                response.setError("초안 이력을 찾지 못했습니다.");
                return response;
            }
            PersonaProperties persona = resolvePersona(history.getPersonaName());
            if (persona == null) {
                response.setError("발행할 봇 페르소나 설정을 찾지 못했습니다.");
                return response;
            }

            populatePublishResponse(response, history);

            if (resolveBoardTitle(history.getBoardTitle(), persona) == null) {
                response.setError("현재는 설정된 대상 게시판의 초안만 발행할 수 있습니다.");
                return response;
            }
            if (STATUS_PUBLISHED.equals(history.getStatus())) {
                response.setError("이미 발행된 초안입니다.");
                return response;
            }
            if (!isPublishableStatus(history.getStatus())) {
                response.setError("발행 가능한 초안 상태가 아닙니다.");
                return response;
            }

            JsonNode result = parseJson(history.getRawJson());
            if (result == null || result.isMissingNode() || result.isNull()) {
                response.setError("저장된 초안 JSON을 읽지 못했습니다.");
                return response;
            }

            if (MODE_POST.equals(normalizeMode(history.getGenerationMode()))) {
                BoardDTO post = buildPostForPublish(persona, result);
                if (!StringUtils.hasText(post.getTitle()) || !StringUtils.hasText(post.getContent())) {
                    response.setError("발행할 게시글 제목 또는 본문이 비어 있습니다.");
                    return response;
                }
                boardService.submitPost(history.getBoardTitle(), post);
                BoardDTO created = findPublishedPost(history.getBoardTitle(), post.getTitle(), persona.getName());
                Integer publishedPostNum = created == null ? null : created.getPostNum();
                markPublished(historyId, publishedPostNum);
                populatePublishedResponse(response, publishedPostNum,
                        buildPostRedirectUrl(history.getBoardTitle(), publishedPostNum));
                return response;
            }

            boolean shouldReply = resolveShouldReply(history.getGenerationMode(), result);
            if (!shouldReply) {
                markPublished(historyId, history.getTargetPostNum());
                populatePublishedResponse(response, history.getTargetPostNum(),
                        buildReadPostRedirectUrl(history.getBoardTitle(), history.getTargetPostNum()));
                return response;
            }
            if (history.getTargetPostNum() == null || history.getTargetPostNum() <= 0) {
                response.setError("댓글 초안의 대상 게시글 번호가 없습니다.");
                return response;
            }
            BoardDTO targetPost = boardService.readPost(history.getBoardTitle(), history.getTargetPostNum());
            if (targetPost == null) {
                response.setError("댓글 대상 게시글을 찾지 못했습니다.");
                return response;
            }

            CommentDTO comment = new CommentDTO();
            comment.setPostNum(history.getTargetPostNum());
            comment.setNickname(persona.getName());
            comment.setPassword(botProperties.getPublishGuestPassword());
            comment.setContent(safeCommentForPublish(persona, textOrNull(result.path("reply").path("body"))));
            if (!StringUtils.hasText(comment.getContent())) {
                response.setError("발행할 댓글 본문이 비어 있습니다.");
                return response;
            }
            boardService.addComment(history.getBoardTitle(), comment);
            boardService.updateCommentCount(history.getBoardTitle(), history.getTargetPostNum());
            markPublished(historyId, history.getTargetPostNum());
            populatePublishedResponse(response, history.getTargetPostNum(),
                    buildReadPostRedirectUrl(history.getBoardTitle(), history.getTargetPostNum()));
            return response;
        } catch (Exception e) {
            log.error("봇 초안 발행 실패. historyId={}", historyId, e);
            response.setError("봇 초안 발행 중 오류가 발생했습니다.");
            return response;
        }
    }

    public List<AssistantBotHistoryDTO> getRecentHistory(int days, int limit) {
        return safeList(assistantBotMapper.selectRecentHistorySince(resolveHistorySince(days), resolveHistoryLimit(limit)));
    }

    public List<AssistantBotHistorySummaryDTO> getHistorySummary(int days) {
        return safeList(assistantBotMapper.selectHistorySummarySince(resolveHistorySince(days)));
    }

    public AssistantBotAutoPublishStatusDTO getAutoPublishStatus() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        LocalDateTime since = today.atStartOfDay();
        LocalDateTime minuteStart = now.withSecond(0).withNano(0);

        AssistantBotAutoPublishStatusDTO status = new AssistantBotAutoPublishStatusDTO();
        status.setEnabled(botProperties.isEnabled());
        status.setAutoPublishEnabled(botProperties.isAutoPublishEnabled());
        status.setAutoPublishCatchUpEnabled(botProperties.isAutoPublishCatchUpEnabled());
        status.setAutoPublishCron(botProperties.getAutoPublishCron());
        status.setAutoPublishZone(botProperties.getAutoPublishZone());
        status.setPostDailyLimit(Math.max(0, botProperties.getAutoPublishPostDailyLimit()));
        status.setCommentDailyLimit(Math.max(0, botProperties.getAutoPublishCommentDailyLimit()));
        status.setChatDailyLimit(Math.max(0, botProperties.getAutoPublishChatDailyLimit()));
        status.setDate(today);
        status.setServerNow(now);

        List<AssistantBotAutoPublishStatusDTO.PersonaStatusDTO> personaStatuses = new ArrayList<>();
        for (PersonaProperties persona : safeList(botProperties.getEnabledPersonas())) {
            if (persona == null) {
                continue;
            }
            personaStatuses.add(buildPersonaAutoPublishStatus(persona, today, now, since, minuteStart));
        }
        status.setPersonas(personaStatuses);
        return status;
    }

    private AssistantBotAutoPublishStatusDTO.PersonaStatusDTO buildPersonaAutoPublishStatus(PersonaProperties persona,
                                                                                           LocalDate today,
                                                                                           LocalDateTime now,
                                                                                           LocalDateTime since,
                                                                                           LocalDateTime minuteStart) {
        String boardTitle = resolveBoardTitle(persona.getBoardTitle(), persona);
        AssistantBotAutoPublishStatusDTO.PersonaStatusDTO personaStatus = new AssistantBotAutoPublishStatusDTO.PersonaStatusDTO();
        personaStatus.setPersonaName(persona.getName());
        personaStatus.setBoardTitle(boardTitle);
        personaStatus.setModel(resolveModel(persona));

        if (!StringUtils.hasText(boardTitle)) {
            personaStatus.setWaitingDetail("invalid_board");
            return personaStatus;
        }

        if (isChatPersona(persona)) {
            personaStatus.setPublishChannel(MODE_CHAT);
            int chatDailyLimit = Math.max(0, botProperties.getAutoPublishChatDailyLimit());
            int handledChatToday = assistantBotMapper.countGeneratedSinceByMode(persona.getName(), boardTitle, MODE_CHAT, since);
            int handledChatThisMinute = assistantBotMapper.countGeneratedSinceByMode(persona.getName(), boardTitle, MODE_CHAT, minuteStart);
            personaStatus.setHandledChatToday(handledChatToday);
            personaStatus.setHandledChatThisMinute(handledChatThisMinute);

            List<Integer> chatSlots = resolveChatAutoPublishSlots(persona, today, chatDailyLimit);
            personaStatus.setChatSlots(formatMinuteSlots(chatSlots));
            Integer nextChatSlot = resolveNextUpcomingChatSlot(persona, today, now.toLocalTime(), chatDailyLimit);
            personaStatus.setNextChatSlot(nextChatSlot == null ? null : formatMinuteOfDay(nextChatSlot));

            boolean chatDue = isPublishMinute(chatSlots, toMinuteOfDay(now.toLocalTime()), handledChatToday, handledChatThisMinute);
            personaStatus.setDueModes(chatDue ? Collections.singletonList(MODE_CHAT) : Collections.emptyList());
            personaStatus.setWaitingDetail(chatDue
                    ? "due_now"
                    : buildChatWaitingDetail(persona, today, now.toLocalTime(), handledChatToday, chatDailyLimit));
            return personaStatus;
        }

        personaStatus.setPublishChannel("board");
        int handledPostToday = assistantBotMapper.countPublishedSinceByMode(persona.getName(), boardTitle, MODE_POST, since);
        int handledCommentToday = assistantBotMapper.countGeneratedSinceByMode(persona.getName(), boardTitle, MODE_COMMENT, since);
        int handledPostThisMinute = assistantBotMapper.countGeneratedSinceByMode(persona.getName(), boardTitle, MODE_POST, minuteStart);
        int handledCommentThisMinute = assistantBotMapper.countGeneratedSinceByMode(persona.getName(), boardTitle, MODE_COMMENT, minuteStart);
        int handledPostRecoveryCooldown = assistantBotMapper.countGeneratedSinceByMode(
                persona.getName(),
                boardTitle,
                MODE_POST,
                now.minusMinutes(Math.max(1, botProperties.getAutoPublishCatchUpRecoveryCooldownMinutes()))
        );

        personaStatus.setHandledPostToday(handledPostToday);
        personaStatus.setHandledCommentToday(handledCommentToday);
        personaStatus.setHandledPostThisMinute(handledPostThisMinute);
        personaStatus.setHandledCommentThisMinute(handledCommentThisMinute);
        personaStatus.setHandledPostRecoveryCooldown(handledPostRecoveryCooldown);

        List<Integer> postSlots = resolveAutoPublishSlots(persona, today, MODE_POST, Math.max(0, botProperties.getAutoPublishPostDailyLimit()));
        List<Integer> commentSlots = resolveAutoPublishSlots(persona, today, MODE_COMMENT, Math.max(0, botProperties.getAutoPublishCommentDailyLimit()));
        personaStatus.setPostSlots(formatMinuteSlots(postSlots));
        personaStatus.setCommentSlots(formatMinuteSlots(commentSlots));

        Integer nextPostSlot = resolveNextUpcomingRandomAutoPublishSlot(persona, today, MODE_POST, now.toLocalTime());
        Integer nextCommentSlot = resolveNextUpcomingRandomAutoPublishSlot(persona, today, MODE_COMMENT, now.toLocalTime());
        personaStatus.setNextPostSlot(nextPostSlot == null ? null : formatMinuteOfDay(nextPostSlot));
        personaStatus.setNextCommentSlot(nextCommentSlot == null ? null : formatMinuteOfDay(nextCommentSlot));

        List<AutoPublishCandidate> dueCandidates = resolveDueAutoPublishCandidates(persona, today, now.toLocalTime(),
                handledPostToday, handledCommentToday, handledPostThisMinute, handledCommentThisMinute,
                handledPostRecoveryCooldown);
        List<String> dueModes = new ArrayList<>();
        for (AutoPublishCandidate candidate : dueCandidates) {
            if (candidate != null && StringUtils.hasText(candidate.mode)) {
                dueModes.add(candidate.mode);
            }
        }
        personaStatus.setDueModes(dueModes);
        personaStatus.setWaitingDetail(dueModes.isEmpty()
                ? buildWaitingDetail(persona, today, now.toLocalTime(), handledPostToday, handledCommentToday)
                : "due_now");
        return personaStatus;
    }

    private LocalDateTime resolveHistorySince(int days) {
        int safeDays = Math.max(1, Math.min(days, 14));
        return LocalDate.now(clock).minusDays(safeDays - 1L).atStartOfDay();
    }

    private int resolveHistoryLimit(int limit) {
        return Math.max(1, Math.min(limit, 200));
    }

    private void populatePublishResponse(AssistantBotPublishResponseDTO response, AssistantBotHistoryDTO history) {
        response.setBoardTitle(history.getBoardTitle());
        response.setMode(history.getGenerationMode());
        response.setStatus(history.getStatus());
        response.setTargetPostNum(history.getTargetPostNum());
        response.setPublishedPostNum(history.getPublishedPostNum());
    }

    private boolean isPublishableStatus(String status) {
        return STATUS_DRAFT.equals(status) || STATUS_SKIPPED.equals(status);
    }

    private BoardDTO buildPostForPublish(PersonaProperties persona, JsonNode result) {
        BoardDTO post = new BoardDTO();
        post.setWriter(persona.getName());
        post.setGuestPassword(botProperties.getPublishGuestPassword());
        post.setTitle(safeTitleForPublish(persona, textOrNull(result.path("post").path("title"))));
        post.setContent(toHtmlBody(persona, textOrNull(result.path("post").path("body"))));
        post.setNotice(0);
        return post;
    }

    private void markPublished(long historyId, Integer publishedPostNum) {
        assistantBotMapper.updateStatus(historyId, STATUS_PUBLISHED, publishedPostNum);
    }

    private void populatePublishedResponse(AssistantBotPublishResponseDTO response,
                                           Integer publishedPostNum,
                                           String redirectUrl) {
        response.setStatus(STATUS_PUBLISHED);
        response.setPublishedPostNum(publishedPostNum);
        response.setRedirectUrl(redirectUrl);
    }

    private String buildPostRedirectUrl(String boardTitle, Integer postNum) {
        if (postNum == null) {
            return "/boards/" + boardTitle;
        }
        return buildReadPostRedirectUrl(boardTitle, postNum);
    }

    private String buildReadPostRedirectUrl(String boardTitle, Integer postNum) {
        return "/boards/" + boardTitle + "/readPost?postNum=" + postNum;
    }

    public AutoPublishResult autoPublishOnce() {
        return summarizeAutoPublishResults(autoPublishAllPersonas());
    }

    public List<AutoPublishResult> autoPublishAllPersonas() {
        if (!botProperties.isEnabled() || !botProperties.isAutoPublishEnabled()) {
            return Collections.singletonList(AutoPublishResult.skipped("autoPublishEnabled=false"));
        }

        List<PersonaProperties> personas = botProperties.getEnabledPersonas();
        if (personas.isEmpty()) {
            return Collections.singletonList(AutoPublishResult.skipped("no_enabled_persona"));
        }

        List<AutoPublishResult> results = new ArrayList<>();
        for (PersonaProperties persona : personas) {
            results.add(autoPublishOnce(persona.getName()));
        }
        return results;
    }

    private AutoPublishResult summarizeAutoPublishResults(List<AutoPublishResult> results) {
        AutoPublishResult lastPublished = null;
        String lastSkippedDetail = null;
        String lastFailureDetail = null;
        String lastFailurePersonaName = null;
        for (AutoPublishResult result : safeList(results)) {
            if (result == null) {
                continue;
            }
            if (STATUS_PUBLISHED.equals(result.getOutcome())) {
                lastPublished = result;
                continue;
            }
            if (OUTCOME_FAILED.equals(result.getOutcome())) {
                lastFailureDetail = result.getDetail();
                lastFailurePersonaName = result.getPersonaName();
                continue;
            }
            lastSkippedDetail = result.getDetail();
        }

        if (lastPublished != null) {
            return lastPublished;
        }
        if (lastFailureDetail != null) {
            return AutoPublishResult.failed(lastFailurePersonaName, lastFailureDetail);
        }
        return AutoPublishResult.skipped(lastSkippedDetail == null ? "no_due_candidate" : lastSkippedDetail);
    }

    AutoPublishResult autoPublishOnce(String personaName) {
        PersonaProperties persona = resolvePersona(personaName);
        if (persona == null) {
            return AutoPublishResult.skipped("invalid_persona");
        }

        String boardTitle = resolveBoardTitle(persona.getBoardTitle(), persona);
        if (!StringUtils.hasText(boardTitle)) {
            return AutoPublishResult.skipped(persona.getName(), "invalid_board");
        }

        if (isChatPersona(persona)) {
            return autoPublishChatOnce(persona, boardTitle);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        LocalDateTime since = today.atStartOfDay();
        LocalDateTime minuteStart = now.withSecond(0).withNano(0);

        int handledPostToday = assistantBotMapper.countPublishedSinceByMode(persona.getName(), boardTitle, MODE_POST, since);
        int handledCommentToday = assistantBotMapper.countGeneratedSinceByMode(persona.getName(), boardTitle, MODE_COMMENT, since);
        int handledPostThisMinute = assistantBotMapper.countGeneratedSinceByMode(persona.getName(), boardTitle, MODE_POST, minuteStart);
        int handledCommentThisMinute = assistantBotMapper.countGeneratedSinceByMode(persona.getName(), boardTitle, MODE_COMMENT, minuteStart);
        int handledPostRecoveryCooldown = assistantBotMapper.countGeneratedSinceByMode(
                persona.getName(),
                boardTitle,
                MODE_POST,
                now.minusMinutes(Math.max(1, botProperties.getAutoPublishCatchUpRecoveryCooldownMinutes()))
        );

        List<AutoPublishCandidate> dueCandidates = resolveDueAutoPublishCandidates(persona, today, now.toLocalTime(),
                handledPostToday, handledCommentToday, handledPostThisMinute, handledCommentThisMinute,
                handledPostRecoveryCooldown);
        if (dueCandidates.isEmpty()) {
            return AutoPublishResult.skipped(persona.getName(),
                    buildWaitingDetail(persona, today, now.toLocalTime(), handledPostToday, handledCommentToday));
        }

        AutoPublishResult lastPublished = null;
        String lastSkippedDetail = null;
        String lastFailureDetail = null;
        for (AutoPublishCandidate candidate : dueCandidates) {
            AssistantBotDraftRequestDTO request = buildAutoDraftRequest(persona, boardTitle, candidate.mode);
            if (request == null) {
                insertAutoPublishSkippedHistory(persona, boardTitle, candidate.mode, null,
                        "no_target_for_mode:" + candidate.mode);
                lastSkippedDetail = "no_target_for_mode:" + candidate.mode;
                continue;
            }

            AssistantBotDraftResponseDTO draft = generateDraftInternal(request, botProperties.getAutoPublishMaxGenerateAttempts(), true);
            if (StringUtils.hasText(draft.getError())) {
                insertAutoPublishSkippedHistory(persona, boardTitle, candidate.mode, request.getTargetPostNum(),
                        "draft_error:" + draft.getError());
                lastFailureDetail = "draft_error:" + draft.getError();
                continue;
            }
            if (STATUS_SKIPPED.equals(draft.getStatus())) {
                if (draft.getAttemptCount() != null && draft.getAttemptCount() > 0) {
                    lastSkippedDetail = AUTO_DRAFT_SKIPPED_AFTER_GENERATION + candidate.mode;
                    continue;
                }
                lastSkippedDetail = draft.getHistoryId() == null
                        ? AUTO_DRAFT_BUDGET_EXCEEDED
                        : "draft_skipped:" + candidate.mode;
                if (draft.getHistoryId() == null) {
                    insertAutoPublishSkippedHistory(persona, boardTitle, candidate.mode, request.getTargetPostNum(),
                            lastSkippedDetail);
                    continue;
                }
                continue;
            }
            if (draft.getHistoryId() == null) {
                insertAutoPublishSkippedHistory(persona, boardTitle, candidate.mode, request.getTargetPostNum(),
                        "missing_history_id");
                lastFailureDetail = "missing_history_id";
                continue;
            }

            AssistantBotPublishResponseDTO published = publishDraft(draft.getHistoryId());
            if (StringUtils.hasText(published.getError())) {
                updateAutoPublishHistoryAsSkipped(draft.getHistoryId());
                lastFailureDetail = "publish_error:" + published.getError();
                continue;
            }
            lastPublished = AutoPublishResult.published(persona.getName(), request.getMode(), draft.getHistoryId(), published.getPublishedPostNum(), published.getRedirectUrl());
        }

        if (lastPublished != null) {
            return lastPublished;
        }
        if (lastFailureDetail != null) {
            return AutoPublishResult.failed(persona.getName(), lastFailureDetail);
        }
        return AutoPublishResult.skipped(persona.getName(), lastSkippedDetail == null ? "no_due_candidate" : lastSkippedDetail);
    }

    AutoPublishResult autoPublishChatOnce(PersonaProperties persona, String boardTitle) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        LocalDateTime since = today.atStartOfDay();
        LocalDateTime minuteStart = now.withSecond(0).withNano(0);

        int chatDailyLimit = Math.max(0, botProperties.getAutoPublishChatDailyLimit());
        int handledChatToday = assistantBotMapper.countGeneratedSinceByMode(persona.getName(), boardTitle, MODE_CHAT, since);
        int handledChatThisMinute = assistantBotMapper.countGeneratedSinceByMode(persona.getName(), boardTitle, MODE_CHAT, minuteStart);
        List<Integer> chatSlots = resolveChatAutoPublishSlots(persona, today, chatDailyLimit);
        if (!isPublishMinute(chatSlots, toMinuteOfDay(now.toLocalTime()), handledChatToday, handledChatThisMinute)) {
            return AutoPublishResult.skipped(persona.getName(),
                    buildChatWaitingDetail(persona, today, now.toLocalTime(), handledChatToday, chatDailyLimit));
        }

        try {
            List<ChatMessageDTO> recentChats = safeList(
                    chatRoomService.getRecentMessages(Math.max(1, botProperties.getChatContextMessageLimit())));
            List<AssistantBotHistoryDTO> recentHistory = safeList(
                    assistantBotMapper.selectRecentHistory(persona.getName(), boardTitle, Math.max(1, botProperties.getRecentHistoryLimit())));

            int maxAttempts = Math.max(1, botProperties.getAutoPublishMaxGenerateAttempts());
            String retryFeedback = null;
            ChatCandidate acceptedCandidate = null;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                GenerateCallBudgetResult budgetResult = tryConsumeGenerateCallBudget();
                if (!budgetResult.isAllowed()) {
                    return AutoPublishResult.skipped(persona.getName(), AUTO_DRAFT_BUDGET_EXCEEDED);
                }
                String prompt = buildChatPrompt(persona, recentChats, recentHistory, retryFeedback, attempt, maxAttempts);
                String rawJson = geminiClient.generateAnswer(
                        prompt,
                        Math.max(1, botProperties.getMaxOutputTokens()),
                        resolveModel(persona)
                );
                ChatCandidate candidate = validateChatCandidate(persona, parseJson(rawJson), recentHistory);
                if (candidate.accepted) {
                    acceptedCandidate = candidate;
                    break;
                }
                retryFeedback = candidate.feedback;
                log.info("봇 채팅 재시도. personaName={}, attempt={}, feedback={}", persona.getName(), attempt, retryFeedback);
            }

            if (acceptedCandidate == null) {
                insertAutoPublishSkippedHistory(persona, boardTitle, MODE_CHAT, null,
                        StringUtils.hasText(retryFeedback) ? "chat_rejected:" + safeText(retryFeedback, 60) : "chat_rejected");
                return AutoPublishResult.failed(persona.getName(), "chat_draft_rejected");
            }

            ChatMessageDTO posted = chatRoomService.postBotMessage(persona.getName(), acceptedCandidate.body);
            if (posted == null) {
                insertAutoPublishSkippedHistory(persona, boardTitle, MODE_CHAT, null, "chat_post_failed");
                return AutoPublishResult.failed(persona.getName(), "chat_post_failed");
            }

            AssistantBotHistoryDTO history = new AssistantBotHistoryDTO();
            history.setPersonaName(persona.getName());
            history.setBoardTitle(boardTitle);
            history.setGenerationMode(MODE_CHAT);
            history.setTargetPostNum(null);
            history.setTopic(acceptedCandidate.topic);
            history.setDraftTitle(null);
            history.setDraftBody(acceptedCandidate.body);
            history.setRawJson(acceptedCandidate.result.toString());
            history.setStatus(STATUS_PUBLISHED);
            assistantBotMapper.insertHistory(history);
            return AutoPublishResult.published(persona.getName(), MODE_CHAT, history.getId(), null, null);
        } catch (GeminiException e) {
            log.error("봇 채팅 Gemini 호출 실패. personaName={}", persona.getName(), e);
            insertAutoPublishSkippedHistory(persona, boardTitle, MODE_CHAT, null, "chat_gemini_error");
            return AutoPublishResult.failed(persona.getName(), "chat_gemini_error");
        } catch (Exception e) {
            log.error("봇 채팅 발행 실패. personaName={}", persona.getName(), e);
            return AutoPublishResult.failed(persona.getName(), "chat_error");
        }
    }

    private List<Integer> resolveChatAutoPublishSlots(PersonaProperties persona, LocalDate date, int dailyLimit) {
        if (persona == null || date == null || dailyLimit <= 0) {
            return Collections.emptyList();
        }
        return botProperties.buildDailyAutoPublishSlots(date, MODE_CHAT, dailyLimit, persona.getBoardTitle(), persona.getName());
    }

    private String buildChatWaitingDetail(PersonaProperties persona,
                                          LocalDate date,
                                          LocalTime now,
                                          int handledChatToday,
                                          int chatDailyLimit) {
        Integer nextSlot = resolveNextUpcomingChatSlot(persona, date, now, chatDailyLimit);
        if (nextSlot == null) {
            return handledChatToday >= chatDailyLimit ? "daily_limits_reached" : "no_remaining_slot_today";
        }
        return "waiting_random_slot:chat=" + formatMinuteOfDay(nextSlot);
    }

    private Integer resolveNextUpcomingChatSlot(PersonaProperties persona, LocalDate date, LocalTime now, int chatDailyLimit) {
        int nowMinuteOfDay = toMinuteOfDay(now);
        for (Integer slot : resolveChatAutoPublishSlots(persona, date, chatDailyLimit)) {
            if (slot != null && slot > nowMinuteOfDay) {
                return slot;
            }
        }
        return null;
    }

    String buildChatPrompt(PersonaProperties persona,
                           List<ChatMessageDTO> recentChats,
                           List<AssistantBotHistoryDTO> recentHistory,
                           String retryFeedback,
                           int attempt,
                           int maxAttempts) {
        StringBuilder sb = new StringBuilder();
        sb.append("너는 SC1Hub 공개채팅방 전용 AI 유저 '")
                .append(persona.getName())
                .append("'의 채팅 에이전트다.\n");
        sb.append("중요: 실제 사람으로 위장하지 말고, 채팅방의 말투와 리듬만 자연스럽게 반영하라.\n");
        sb.append("닉네임은 이미 봇 계정이므로 특정 실존 유저를 흉내 내지 말라.\n");
        sb.append("현재 시도 횟수: ").append(attempt).append('/').append(maxAttempts).append("\n\n");

        sb.append("페르소나 규칙:\n");
        appendLine(sb, "- " + buildPersonaPromptRule(persona));
        sb.append("\n");

        sb.append("목표:\n");
        sb.append("- 공개채팅방에 페르소나 말투로 짧은 채팅 한 줄을 남긴다.\n");
        sb.append("- 아래 최신 채팅을 읽고 자연스럽게 반응하거나, 이어갈 화제가 없으면 페르소나다운 혼잣말을 던진다.\n");
        if (isRaceGamePersona(persona)) {
            sb.append("- ").append(persona.getName()).append("은 스타크래프트 게임 이야기 관점을 유지한다. 채팅 화제가 게임이 아니어도 맞장구 대신 ")
                    .append(resolveRaceLabel(persona)).append(" 시점의 게임 한마디로 반응한다.\n");
        } else if (hasPersonaName(persona, "훈훈봇")) {
            sb.append("- 훈훈봇은 상대 채팅의 감정이나 상황을 받아주고 부담 없는 응원이나 재치 있는 한마디로 반응한다.\n");
        } else if (isHealthPersona(persona)) {
            sb.append("- 건강봇은 채팅 흐름에 맞춰 실천할 수 있는 건강 상식 한 조각만 한 줄로 툭 건넨다. 줄바꿈·긴 설명·번호 나열 없이 짧게 쓴다.\n");
        }
        sb.append("\n");

        sb.append("금지:\n");
        sb.append("- 정치, 혐오, 성적 표현, 실존 유저 공격, 패드립, 과한 분쟁 유도\n");
        sb.append("- 너무 반듯한 AI 문체, 링크, 해시태그\n");
        sb.append("- 최근 자기 채팅과 같은 문장, 같은 어미 반복\n");
        sb.append("- 금칙어 사용\n\n");

        sb.append("형식 규칙:\n");
        sb.append("- 반드시 한 줄만 쓴다. 줄바꿈을 쓰지 않는다.\n");
        if (isSingleSentenceOnlyPersona(persona)) {
            sb.append("- 한 문장만 짧고 건조하게 쓴다.\n");
        } else {
            sb.append("- 1~2문장, 120자 이내로 짧게 쓴다.\n");
        }
        sb.append("\n");

        if (StringUtils.hasText(retryFeedback)) {
            sb.append("직전 시도 보정 지시:\n");
            appendLine(sb, "- " + retryFeedback);
            sb.append("- 이번 시도에서는 문장과 화제를 분명히 바꾼다.\n\n");
        }

        sb.append("최신 채팅:\n");
        List<ChatMessageDTO> safeChats = safeList(recentChats);
        if (safeChats.isEmpty()) {
            sb.append("- 없음 (조용한 채팅방이니 혼잣말로 시작한다)\n");
        } else {
            int index = 1;
            for (ChatMessageDTO chat : safeChats) {
                if (chat == null) {
                    continue;
                }
                String nickname = StringUtils.hasText(chat.getNickname()) ? chat.getNickname() : "익명";
                appendLine(sb, index + ". [" + nickname + "] " + safeText(chat.getContent(), 120));
                index++;
            }
        }

        sb.append("\n최근 ").append(persona.getName()).append(" 채팅:\n");
        int historyIndex = 1;
        for (AssistantBotHistoryDTO history : safeList(recentHistory)) {
            if (history == null || !MODE_CHAT.equals(normalizeMode(history.getGenerationMode()))
                    || !StringUtils.hasText(history.getDraftBody())) {
                continue;
            }
            appendLine(sb, historyIndex + ". " + safeText(history.getDraftBody(), 120));
            historyIndex++;
            if (historyIndex > 8) {
                break;
            }
        }
        if (historyIndex == 1) {
            sb.append("- 없음\n");
        }

        sb.append("\n출력은 반드시 JSON 하나만 반환한다.\n");
        sb.append("{\"analysis\":{\"topic\":\"\",\"risk_notes\":[]},\"chat\":{\"body\":\"\"}}\n");
        sb.append("JSON 이외의 설명, 코드블록, 사족은 절대 출력하지 마라.\n");

        return truncatePrompt(sb.toString());
    }

    private ChatCandidate validateChatCandidate(PersonaProperties persona,
                                                JsonNode result,
                                                List<AssistantBotHistoryDTO> recentHistory) {
        if (result == null || result.isMissingNode() || result.isNull()) {
            return ChatCandidate.rejected("AI가 유효한 JSON을 반환하지 않았습니다.");
        }

        String body = normalizeChatBody(persona, textOrNull(result.path("chat").path("body")));
        if (!StringUtils.hasText(body)) {
            return ChatCandidate.rejected("채팅 본문이 비어 있습니다.");
        }
        String matchedBlockedWord = findBlockedWord(body);
        if (matchedBlockedWord != null) {
            return ChatCandidate.rejected("금칙어가 포함됐습니다: " + matchedBlockedWord);
        }

        double threshold = clampThreshold(botProperties.getDuplicateSimilarityThreshold());
        String normalizedBody = normalizeText(body);
        for (AssistantBotHistoryDTO history : safeList(recentHistory)) {
            if (history == null || !MODE_CHAT.equals(normalizeMode(history.getGenerationMode()))) {
                continue;
            }
            if (isTooSimilar(normalizedBody, normalizeText(history.getDraftBody()), threshold)) {
                return ChatCandidate.rejected("최근 봇 채팅과 너무 비슷합니다.");
            }
        }

        return ChatCandidate.accepted(result, body, textOrNull(result.path("analysis").path("topic")));
    }

    private String normalizeChatBody(PersonaProperties persona, String body) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        String normalized = body.trim()
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        if (isSingleSentenceOnlyPersona(persona)) {
            normalized = firstSentenceOnly(normalized);
        }
        if (normalized != null && normalized.length() > CHAT_BODY_MAX_CHARS) {
            normalized = normalized.substring(0, CHAT_BODY_MAX_CHARS).trim();
        }
        return normalized;
    }

    private boolean isChatPersona(PersonaProperties persona) {
        // 모든 페르소나를 공개채팅방으로 라우팅한다. 게시판 자동 발행은 사용하지 않는다.
        return persona != null;
    }

    private List<AutoPublishCandidate> resolveDueAutoPublishCandidates(PersonaProperties persona,
                                                                       LocalDate date,
                                                                       LocalTime now,
                                                                       int handledPostToday,
                                                                       int handledCommentToday,
                                                                       int handledPostThisMinute,
                                                                       int handledCommentThisMinute,
                                                                       int handledPostRecoveryCooldown) {
        List<AutoPublishCandidate> candidates = new ArrayList<>();
        int nowMinuteOfDay = toMinuteOfDay(now);

        int postDailyLimit = Math.max(0, botProperties.getAutoPublishPostDailyLimit());
        List<Integer> postSlots = resolveAutoPublishSlots(persona, date, MODE_POST, postDailyLimit);
        if (isPublishMinute(postSlots, nowMinuteOfDay, handledPostToday, handledPostThisMinute, postDailyLimit)
                || shouldRecoverMissedPostAfterLastSlot(postSlots, nowMinuteOfDay, handledPostToday,
                handledPostThisMinute, handledPostRecoveryCooldown, postDailyLimit)) {
            candidates.add(new AutoPublishCandidate(MODE_POST, nowMinuteOfDay));
        }

        int commentDailyLimit = Math.max(0, botProperties.getAutoPublishCommentDailyLimit());
        List<Integer> commentSlots = resolveAutoPublishSlots(persona, date, MODE_COMMENT, commentDailyLimit);
        if (isPublishMinute(commentSlots, nowMinuteOfDay, handledCommentToday, handledCommentThisMinute)) {
            candidates.add(new AutoPublishCandidate(MODE_COMMENT, nowMinuteOfDay));
        }

        candidates.sort(Comparator.comparingInt(candidate -> candidate.minuteOfDay));
        return candidates;
    }

    private String buildWaitingDetail(PersonaProperties persona,
                                      LocalDate date,
                                      LocalTime now,
                                      int handledPostToday,
                                      int handledCommentToday) {
        Integer nextPostSlot = resolveNextUpcomingRandomAutoPublishSlot(persona, date, MODE_POST, now);
        Integer nextCommentSlot = resolveNextUpcomingRandomAutoPublishSlot(persona, date, MODE_COMMENT, now);
        if (nextPostSlot == null && nextCommentSlot == null) {
            return (handledPostToday >= botProperties.getAutoPublishPostDailyLimit()
                    && handledCommentToday >= botProperties.getAutoPublishCommentDailyLimit())
                    ? "daily_limits_reached"
                    : "no_remaining_slot_today";
        }

        List<String> parts = new ArrayList<>();
        if (nextPostSlot != null && toMinuteOfDay(now) < nextPostSlot) {
            parts.add("post=" + formatMinuteOfDay(nextPostSlot));
        }
        if (nextCommentSlot != null && toMinuteOfDay(now) < nextCommentSlot) {
            parts.add("comment=" + formatMinuteOfDay(nextCommentSlot));
        }
        if (parts.isEmpty()) {
            return "waiting_next_due_slot";
        }
        return "waiting_random_slot:" + String.join(",", parts);
    }

    private Integer resolveNextUpcomingRandomAutoPublishSlot(PersonaProperties persona,
                                                             LocalDate date,
                                                             String mode,
                                                             LocalTime now) {
        List<Integer> slots = resolveAutoPublishSlots(
                persona,
                date,
                mode,
                MODE_POST.equals(mode) ? botProperties.getAutoPublishPostDailyLimit() : botProperties.getAutoPublishCommentDailyLimit()
        );
        int nowMinuteOfDay = toMinuteOfDay(now);
        for (Integer slot : slots) {
            if (slot != null && slot > nowMinuteOfDay) {
                return slot;
            }
        }
        return null;
    }

    private int toMinuteOfDay(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    private boolean isPublishMinute(List<Integer> slots,
                                    int nowMinuteOfDay,
                                    int handledToday,
                                    int handledThisMinute) {
        if (slots == null || slots.isEmpty() || handledThisMinute > 0) {
            return false;
        }
        int dueSlotCount = countSlotsAtOrBeforeMinute(slots, nowMinuteOfDay);
        if (botProperties.isAutoPublishCatchUpEnabled()) {
            return dueSlotCount > 0 && handledToday < dueSlotCount;
        }
        return hasSlotAtMinute(slots, nowMinuteOfDay) && handledToday < dueSlotCount;
    }

    private boolean isPublishMinute(List<Integer> slots,
                                    int nowMinuteOfDay,
                                    int handledToday,
                                    int handledThisMinute,
                                    int dailyLimit) {
        if (slots == null || slots.isEmpty() || dailyLimit <= 0 || handledThisMinute > 0) {
            return false;
        }
        int dueSlotCount = countSlotsAtOrBeforeMinute(slots, nowMinuteOfDay);
        if (botProperties.isAutoPublishCatchUpEnabled()) {
            return hasSlotAtMinute(slots, nowMinuteOfDay)
                    && handledToday < Math.min(Math.max(0, dailyLimit), dueSlotCount);
        }
        return hasSlotAtMinute(slots, nowMinuteOfDay) && handledToday < dueSlotCount;
    }

    private int countSlotsAtOrBeforeMinute(List<Integer> slots, int minuteOfDay) {
        int count = 0;
        for (Integer slot : slots) {
            if (slot != null && slot <= minuteOfDay) {
                count++;
            }
        }
        return count;
    }

    private boolean hasSlotAtMinute(List<Integer> slots, int minuteOfDay) {
        for (Integer slot : slots) {
            if (slot != null && slot == minuteOfDay) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldRecoverMissedPostAfterLastSlot(List<Integer> slots,
                                                         int nowMinuteOfDay,
                                                         int handledToday,
                                                         int handledThisMinute,
                                                         int handledRecoveryCooldown,
                                                         int dailyLimit) {
        if (!botProperties.isAutoPublishCatchUpEnabled()
                || slots == null
                || slots.isEmpty()
                || dailyLimit <= 0
                || handledThisMinute > 0
                || handledToday >= dailyLimit
                || handledRecoveryCooldown > 0) {
            return false;
        }
        Integer lastSlot = slots.get(slots.size() - 1);
        return lastSlot != null && nowMinuteOfDay > lastSlot;
    }

    private List<Integer> resolveAutoPublishSlots(PersonaProperties persona,
                                                  LocalDate date,
                                                  String mode,
                                                  int dailyLimit) {
        if (persona == null || date == null || !StringUtils.hasText(mode) || dailyLimit <= 0) {
            return Collections.emptyList();
        }
        if (MODE_COMMENT.equals(mode) && !supportsAutoComment(persona)) {
            return Collections.emptyList();
        }

        List<Integer> primarySlots = botProperties.buildDailyAutoPublishSlots(
                date,
                mode,
                dailyLimit,
                persona.getBoardTitle(),
                persona.getName()
        );
        if (!MODE_POST.equals(mode) || !botProperties.isAutoPublishCatchUpEnabled()) {
            return primarySlots;
        }

        int retrySlots = Math.max(0, botProperties.getAutoPublishCatchUpPostRetrySlots());
        if (retrySlots <= 0 || primarySlots.isEmpty()) {
            return primarySlots;
        }

        int lastPrimarySlot = primarySlots.get(primarySlots.size() - 1);
        if (lastPrimarySlot >= (24 * 60) - 1) {
            return primarySlots;
        }

        List<Integer> catchUpSlots = botProperties.buildDailyAutoPublishSlotsInRange(
                date,
                mode + "_catchup",
                retrySlots,
                persona.getBoardTitle(),
                persona.getName(),
                lastPrimarySlot + 1,
                (24 * 60) - 1
        );
        if (catchUpSlots.isEmpty()) {
            return primarySlots;
        }

        List<Integer> combinedSlots = new ArrayList<>(primarySlots);
        combinedSlots.addAll(catchUpSlots);
        Collections.sort(combinedSlots);
        return combinedSlots;
    }

    private List<String> formatMinuteSlots(List<Integer> slots) {
        if (slots == null || slots.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> formatted = new ArrayList<>();
        for (Integer slot : slots) {
            if (slot == null) {
                continue;
            }
            formatted.add(formatMinuteOfDay(slot));
        }
        return formatted;
    }

    private String formatMinuteOfDay(int minuteOfDay) {
        return String.format("%02d:%02d", minuteOfDay / 60, minuteOfDay % 60);
    }

    private CandidateDraft validateCandidate(String mode,
                                             JsonNode result,
                                             List<BoardDTO> recentPosts,
                                             List<CommentDTO> recentComments,
                                             List<AssistantBotHistoryDTO> recentHistory) {
        return validateCandidate(null, mode, result, recentPosts, recentComments, recentHistory);
    }

    private CandidateDraft validateCandidate(PersonaProperties persona,
                                             String mode,
                                             JsonNode result,
                                             List<BoardDTO> recentPosts,
                                             List<CommentDTO> recentComments,
                                             List<AssistantBotHistoryDTO> recentHistory) {
        if (result == null || result.isMissingNode() || result.isNull()) {
            return CandidateDraft.rejected("AI가 유효한 JSON을 반환하지 않았습니다.");
        }

        String title = extractGeneratedTitle(mode, result);
        String body = extractGeneratedBody(mode, result);
        boolean shouldReply = resolveShouldReply(mode, result);
        if (MODE_POST.equals(mode) && !StringUtils.hasText(title)) {
            return CandidateDraft.rejected("제목이 비어 있습니다.");
        }
        if (!StringUtils.hasText(body) && (MODE_POST.equals(mode) || shouldReply)) {
            return CandidateDraft.rejected("본문이 비어 있습니다.");
        }
        String matchedBlockedWord = findBlockedWord(title, body);
        if (matchedBlockedWord != null) {
            return CandidateDraft.rejected("금칙어가 포함됐습니다: " + matchedBlockedWord);
        }
        String selfReviewIssue = resolveSelfReviewIssue(mode, result);
        if (selfReviewIssue != null) {
            return CandidateDraft.rejected(selfReviewIssue);
        }
        String personaScopeIssue = resolvePersonaScopeIssue(persona, title, body);
        if (personaScopeIssue != null) {
            return CandidateDraft.rejected(personaScopeIssue);
        }

        if (!isRepetitiveByDesignPersona(persona)) {
            KeywordOverlapCheck keywordOverlapCheck = findRecentTitleKeywordIssue(mode, title, recentPosts, result);
            if (keywordOverlapCheck.invalid) {
                return CandidateDraft.rejected(keywordOverlapCheck.feedback);
            }

            DuplicateCheck duplicateCheck = findDuplicateIssue(mode, title, body, recentPosts, recentComments, recentHistory);
            if (duplicateCheck.duplicate) {
                return CandidateDraft.rejected(duplicateCheck.feedback);
            }
        }

        CandidateDraft draft = new CandidateDraft();
        draft.accepted = true;
        draft.result = result;
        draft.title = title;
        draft.body = body;
        draft.topic = extractTopic(mode, result);
        draft.shouldReply = shouldReply;
        return draft;
    }

    private String buildPrompt(PersonaProperties persona,
                               String mode,
                               String boardTitle,
                               BoardDTO targetPost,
                               List<BoardDTO> recentPosts,
                               List<CommentDTO> recentComments,
                               List<AssistantBotHistoryDTO> recentHistory,
                               String retryFeedback,
                               int attempt,
                               int maxAttempts) {
        StringBuilder sb = new StringBuilder();
        boolean useRecentContext = shouldUseRecentContext(persona);
        String recommendedTopicLane = MODE_POST.equals(mode)
                ? recommendPostTopicLane(recentHistory, attempt)
                : null;
        String recommendedPostStrategy = MODE_POST.equals(mode)
                ? recommendPostStrategy(persona, recentPosts, attempt)
                : null;
        sb.append("너는 SC1Hub 꿀잼놀이터 게시판 전용 AI 유저 '")
                .append(persona.getName())
                .append("'의 글 작성 에이전트다.\n");
        sb.append("중요: 실제 사람으로 위장하지 말고, 게시판의 말투와 리듬만 자연스럽게 반영하라.\n");
        sb.append("닉네임은 이미 봇 계정이므로 특정 실존 유저를 흉내 내지 말라.\n");
        sb.append("현재 시도 횟수: ").append(attempt).append('/').append(maxAttempts).append("\n\n");

        sb.append("페르소나 규칙:\n");
        appendLine(sb, "- " + buildPersonaPromptRule(persona));
        if (MODE_POST.equals(mode)) {
            appendLine(sb, "- " + buildPersonaPostStyleRule(persona));
        }
        if ("저묵묵봇".equals(String.valueOf(persona.getName()))) {
            appendLine(sb, "- 제목, 본문, 댓글 모두 한 문장만 쓴다. 줄바꿈도 쓰지 말고 짧고 건조하게 끝낸다.");
        }
        sb.append("\n");

        appendPersonaGoalRules(sb, persona);

        if (MODE_POST.equals(mode)) {
            appendPostTopicGuide(sb, persona, recommendedTopicLane, recommendedPostStrategy);
        }

        sb.append("금지:\n");
        sb.append("- 정치, 혐오, 성적 표현, 실존 유저 공격, 패드립, 과한 분쟁 유도\n");
        sb.append("- 너무 반듯한 AI 문체\n");
        sb.append("- 반복되는 제목 패턴\n");
        sb.append("- 최근 글과 유사한 주제/표현 재사용\n");
        sb.append("- 금칙어 사용\n\n");

        sb.append("중복 회피 규칙:\n");
        sb.append("- 최근 ").append(persona.getName()).append(" 초안의 제목, 화제, 본문 전개를 재사용하지 않는다.\n");
        sb.append("- 최근 게시글 제목과 너무 비슷한 제목을 만들지 않는다.\n");
        sb.append("- 최근 게시글과 제목 첫머리 두세 단어, 도입 리듬, 문장 골격까지 겹치지 않게 바꾼다.\n");
        sb.append("- 최근 댓글과 같은 결론, 어미, 첫 문장을 반복하지 않는다.\n\n");

        if (MODE_POST.equals(mode) && !isRepetitiveByDesignPersona(persona)) {
            sb.append("제목 오프닝 규칙:\n");
            sb.append("- '요즘 들어', '다들', '오늘'처럼 익숙한 출발을 반복하지 말고 제목 시작 어휘를 매번 바꾼다.\n");
            sb.append("- 최근 제목과 첫 2~3어절이 같거나 거의 같으면 실패로 간주하고 새로 쓴다.\n");
            appendLine(sb, "- " + buildRecentTitleOpeningHint(recentPosts, recentHistory));
            sb.append("\n");

            sb.append("최신글 연계/신규 전략 규칙:\n");
            sb.append("- analysis.post_strategy 에는 반드시 linked 또는 fresh 중 하나를 넣는다.\n");
            sb.append("- linked: 최신글 화제를 옆으로 이어받는 반응글이다. 최신글 제목 단어는 최대 1개만 앵커로 재사용하고, 나머지 표현은 새로 바꾼다.\n");
            sb.append("- fresh: 최신글 제목에서 많이 나온 단어와 화제를 피하고 다른 소재로 간다.\n");
            appendLine(sb, "- " + buildRecentTitleKeywordHint(recentPosts, recommendedPostStrategy));
            sb.append("\n");
        }

        appendHumanStyleRules(sb, persona);
        appendFormatRules(sb, persona);

        if (StringUtils.hasText(retryFeedback)) {
            sb.append("직전 시도 보정 지시:\n");
            appendLine(sb, "- " + retryFeedback);
            sb.append("- 이번 시도에서는 제목, 첫 문장, 핵심 감정선을 분명히 바꾼다.\n\n");
        }

        sb.append("입력:\n");
        if (useRecentContext) {
            sb.append("1. 최근 게시글/댓글 데이터\n");
            sb.append("2. 최근 작성한 ").append(persona.getName()).append(" 글 목록\n");
            sb.append("3. 오늘의 생성 모드(").append(MODE_COMMENT.equals(mode) ? "댓글" : "게시글").append(")\n");
            sb.append("4. 게시판 이름(").append(boardTitle).append(")\n\n");
        } else {
            sb.append("1. 오늘의 생성 모드(").append(MODE_COMMENT.equals(mode) ? "댓글" : "게시글").append(")\n");
            sb.append("2. 게시판 이름(").append(boardTitle).append(")\n");
            sb.append("3. 야옹봇은 최근 게시글, 댓글, 이전 초안에 반응하지 않는다.\n\n");
        }

        if (useRecentContext && targetPost != null) {
            sb.append("대상 게시글:\n");
            appendLine(sb, "- 작성자: " + safeText(targetPost.getWriter(), 60));
            appendLine(sb, "- 제목: " + safeText(targetPost.getTitle(), 160));
            appendLine(sb, "- 본문: " + safeText(stripHtml(targetPost.getContent()), botProperties.getPromptExcerptChars()));
            sb.append("\n");
        }

        if (useRecentContext) {
            sb.append("최근 게시글:\n");
            if (recentPosts.isEmpty()) {
                sb.append("- 없음\n");
            } else {
                int index = 1;
                for (BoardDTO post : recentPosts) {
                    if (post == null) {
                        continue;
                    }
                    appendLine(sb, index + ". 제목: " + safeText(post.getTitle(), 120));
                    appendLine(sb, "   본문: " + safeText(stripHtml(post.getContent()), botProperties.getPromptExcerptChars()));
                    index++;
                }
            }

            sb.append("\n최근 댓글:\n");
            if (recentComments.isEmpty()) {
                sb.append("- 없음\n");
            } else {
                int index = 1;
                for (CommentDTO comment : recentComments) {
                    if (comment == null) {
                        continue;
                    }
                    String nickname = StringUtils.hasText(comment.getNickname()) ? comment.getNickname() : "익명";
                    appendLine(sb, index + ". [" + nickname + "] " + safeText(stripHtml(comment.getContent()), 160));
                    index++;
                }
            }

            sb.append("\n최근 ").append(persona.getName()).append(" 초안:\n");
            if (recentHistory.isEmpty()) {
                sb.append("- 없음\n");
            } else {
                int index = 1;
                for (AssistantBotHistoryDTO history : recentHistory) {
                    if (history == null) {
                        continue;
                    }
                    String label = MODE_POST.equals(normalizeMode(history.getGenerationMode())) ? "게시글" : "댓글";
                    appendLine(sb, index + ". [" + label + "] 주제=" + safeText(history.getTopic(), 80));
                    if (StringUtils.hasText(history.getDraftTitle())) {
                        appendLine(sb, "   제목: " + safeText(history.getDraftTitle(), 100));
                    }
                    appendLine(sb, "   본문: " + safeText(history.getDraftBody(), 160));
                    index++;
                }
            }
        }

        if (MODE_POST.equals(mode) && useRecentContext) {
            String topicBalanceHint = buildTopicBalanceHint(persona, recentHistory);
            if (StringUtils.hasText(topicBalanceHint)) {
                sb.append("\n최근 주제 균형 힌트:\n");
                appendLine(sb, "- " + topicBalanceHint);
            }
        } else {
            if (useRecentContext) {
                sb.append("\n댓글 스레드 힌트:\n");
                appendLine(sb, "- " + buildCommentThreadHint(persona, targetPost, recentComments));
                sb.append("\n댓글 상호작용 규칙:\n");
                appendLine(sb, "- " + buildCommentInteractionRule(persona, targetPost));
                appendCommentFollowupRules(sb, persona);
            } else if (MODE_COMMENT.equals(mode)) {
                sb.append("\n댓글 규칙:\n");
                appendLine(sb, "- " + buildCommentInteractionRule(persona, targetPost));
                appendCommentFollowupRules(sb, persona);
            }
        }

        appendWorkProcedureRules(sb, persona);

        sb.append("출력은 반드시 JSON 하나만 반환한다.\n");
        if (MODE_POST.equals(mode)) {
            sb.append("{\"analysis\":{\"memes\":[],\"topic\":\"\",\"post_strategy\":\"\",\"risk_notes\":[]},");
            sb.append("\"post\":{\"title\":\"\",\"body\":\"\"},");
            sb.append("\"self_review\":{\"naturalness\":0,\"novelty\":0,\"engagement\":0,\"needs_revision\":false}}\n");
        } else {
            sb.append("{\"analysis\":{\"comment_type\":\"\",\"tone_match\":\"\",\"risk_notes\":[]},");
            sb.append("\"reply\":{\"should_reply\":true,\"body\":\"\"},");
            sb.append("\"self_review\":{\"naturalness\":0,\"context_fit\":0,\"conflict_risk\":0,\"needs_revision\":false}}\n");
        }
        sb.append("JSON 이외의 설명, 코드블록, 사족은 절대 출력하지 마라.\n");

        return truncatePrompt(sb.toString());
    }

    private String resolveSelfReviewIssue(String mode, JsonNode result) {
        JsonNode review = result.path("self_review");
        if (review.isMissingNode() || review.isNull()) {
            return null;
        }

        int minScore = Math.max(0, botProperties.getSelfReviewMinimumScore());
        if (minScore <= 0) {
            return null;
        }

        boolean needsRevision = review.path("needs_revision").asBoolean(false);
        int hardFloor = Math.max(35, minScore - 15);
        if (MODE_POST.equals(mode)) {
            int naturalness = review.path("naturalness").asInt(0);
            int novelty = review.path("novelty").asInt(0);
            int engagement = review.path("engagement").asInt(0);
            int lowCount = countBelow(minScore, naturalness, novelty, engagement);
            boolean hardFail = naturalness < hardFloor || novelty < hardFloor || engagement < hardFloor;
            if (hardFail || lowCount >= 2 || (needsRevision && lowCount >= 1)) {
                return String.format(Locale.ROOT,
                        "self_review 점수가 낮습니다: naturalness=%d, novelty=%d, engagement=%d, needs_revision=%s",
                        naturalness, novelty, engagement, needsRevision);
            }
            return null;
        }

        int naturalness = review.path("naturalness").asInt(0);
        int contextFit = review.path("context_fit").asInt(0);
        int conflictRisk = review.path("conflict_risk").asInt(0);
        int lowCount = countBelow(minScore, naturalness, contextFit);
        boolean hardFail = naturalness < hardFloor || contextFit < hardFloor;
        if (hardFail || lowCount >= 2 || (needsRevision && lowCount >= 1)) {
            return String.format(Locale.ROOT,
                    "self_review 점수가 낮습니다: naturalness=%d, context_fit=%d, conflict_risk=%d, needs_revision=%s",
                    naturalness, contextFit, conflictRisk, needsRevision);
        }
        return null;
    }

    private int countBelow(int threshold, int... scores) {
        int count = 0;
        for (int score : scores) {
            if (score < threshold) {
                count++;
            }
        }
        return count;
    }

    private AssistantBotDraftRequestDTO buildAutoDraftRequest(PersonaProperties persona, String boardTitle, String mode) {
        AssistantBotDraftRequestDTO request = new AssistantBotDraftRequestDTO();
        request.setPersonaName(persona.getName());
        request.setBoardTitle(boardTitle);
        request.setMode(mode);
        request.setRecentPostLimit(botProperties.getRecentPostLimit());
        request.setRecentCommentLimit(botProperties.getRecentCommentLimit());

        if (MODE_COMMENT.equals(mode)) {
            if (!supportsAutoComment(persona)) {
                return null;
            }
            Integer targetPostNum = pickAutoCommentTarget(persona, boardTitle);
            if (targetPostNum == null || targetPostNum <= 0) {
                return null;
            }
            request.setTargetPostNum(targetPostNum);
        }
        return request;
    }

    private Integer pickAutoCommentTarget(PersonaProperties persona, String boardTitle) {
        return pickAutoCommentTarget(persona, boardTitle, ThreadLocalRandom.current());
    }

    Integer pickAutoCommentTarget(PersonaProperties persona, String boardTitle, Random random) {
        int candidateLimit = Math.max(1, Math.min(botProperties.getAutoPublishCommentCandidatePosts(), 20));
        try {
            List<BoardDTO> posts = safeList(boardMapper.selectRecentPostsForBot(boardTitle, candidateLimit));
            List<BoardDTO> regularCandidates = new ArrayList<>();
            List<BoardDTO> replyPriorityCandidates = new ArrayList<>();
            for (BoardDTO post : posts) {
                if (post == null || post.getPostNum() <= 0) {
                    continue;
                }
                if (isRaceGamePersona(persona) && !isGameTalkPost(post)) {
                    continue;
                }
                ThreadCommentState threadState = analyzeThreadState(persona, boardTitle, post);
                if (isBotAuthoredPost(post, persona)) {
                    if (threadState.shouldPrioritizeSelfPostReply()) {
                        replyPriorityCandidates.add(post);
                    }
                    continue;
                }
                if (!threadState.canAddCommentToOthersPost()) {
                    continue;
                }
                regularCandidates.add(post);
            }

            List<BoardDTO> allCandidates = new ArrayList<>(regularCandidates);
            allCandidates.addAll(replyPriorityCandidates);
            if (allCandidates.isEmpty()) {
                return null;
            }

            Random safeRandom = random == null ? ThreadLocalRandom.current() : random;
            double priorityProbability = clampProbability(botProperties.getAutoPublishCommentReplyPriorityProbability());
            if (!replyPriorityCandidates.isEmpty() && safeRandom.nextDouble() < priorityProbability) {
                return pickRandomPostNum(replyPriorityCandidates, safeRandom);
            }
            return pickRandomPostNum(allCandidates, safeRandom);
        } catch (Exception e) {
            log.warn("자동 댓글 대상 게시글 조회 실패. boardTitle={}", boardTitle, e);
        }
        return null;
    }

    private Integer pickRandomPostNum(List<BoardDTO> posts, Random random) {
        if (posts.isEmpty()) {
            return null;
        }
        BoardDTO selected = posts.get(random.nextInt(posts.size()));
        return selected == null ? null : selected.getPostNum();
    }

    private boolean isBotAuthoredPost(BoardDTO post, PersonaProperties persona) {
        return post != null && isBotIdentity(post.getWriter(), persona);
    }

    private ThreadCommentState analyzeThreadState(PersonaProperties persona, String boardTitle, BoardDTO post) throws Exception {
        if (post == null || post.getPostNum() <= 0) {
            return ThreadCommentState.empty();
        }
        if (post.getCommentCount() <= 0) {
            return ThreadCommentState.empty();
        }
        int commentLimit = Math.max(10, Math.min(botProperties.getRecentCommentLimit(), 50));
        List<CommentDTO> comments = safeList(boardMapper.selectRecentCommentsForBot(boardTitle, post.getPostNum(), commentLimit));
        CommentDTO latestOwnComment = null;
        int latestOwnIndex = -1;
        CommentDTO latestOtherComment = null;
        int latestOtherIndex = -1;
        for (int i = 0; i < comments.size(); i++) {
            CommentDTO comment = comments.get(i);
            if (comment == null) {
                continue;
            }
            if (isBotIdentity(comment.getNickname(), persona)) {
                if (isLaterComment(comment, i, latestOwnComment, latestOwnIndex)) {
                    latestOwnComment = comment;
                    latestOwnIndex = i;
                }
                continue;
            }
            if (isLaterComment(comment, i, latestOtherComment, latestOtherIndex)) {
                latestOtherComment = comment;
                latestOtherIndex = i;
            }
        }
        boolean hasOwnComment = latestOwnComment != null;
        boolean hasOtherComment = latestOtherComment != null;
        boolean hasNewReplyAfterOwnComment = hasOtherComment
                && (!hasOwnComment || isLaterComment(latestOtherComment, latestOtherIndex, latestOwnComment, latestOwnIndex));
        return new ThreadCommentState(hasNewReplyAfterOwnComment, !hasOwnComment || hasNewReplyAfterOwnComment);
    }

    private boolean isBotIdentity(String nickname, PersonaProperties persona) {
        return StringUtils.hasText(nickname)
                && persona != null
                && StringUtils.hasText(persona.getName())
                && persona.getName().trim().equals(nickname.trim());
    }

    private boolean isLatestCommentByPersona(List<CommentDTO> comments, PersonaProperties persona) {
        CommentDTO latestComment = findLatestComment(comments);
        return latestComment != null && isBotIdentity(latestComment.getNickname(), persona);
    }

    private CommentDTO findLatestComment(List<CommentDTO> comments) {
        if (comments == null || comments.isEmpty()) {
            return null;
        }
        CommentDTO latest = null;
        int latestIndex = -1;
        for (int i = 0; i < comments.size(); i++) {
            CommentDTO comment = comments.get(i);
            if (comment == null) {
                continue;
            }
            if (isLaterComment(comment, i, latest, latestIndex)) {
                latest = comment;
                latestIndex = i;
            }
        }
        return latest;
    }

    private CommentDTO findLatestOtherComment(List<CommentDTO> comments, PersonaProperties persona) {
        if (comments == null || comments.isEmpty()) {
            return null;
        }
        CommentDTO latest = null;
        int latestIndex = -1;
        for (int i = 0; i < comments.size(); i++) {
            CommentDTO comment = comments.get(i);
            if (comment == null || isBotIdentity(comment.getNickname(), persona)) {
                continue;
            }
            if (isLaterComment(comment, i, latest, latestIndex)) {
                latest = comment;
                latestIndex = i;
            }
        }
        return latest;
    }

    private boolean isLaterComment(CommentDTO candidate, int candidateIndex, CommentDTO current, int currentIndex) {
        if (candidate == null) {
            return false;
        }
        if (current == null) {
            return true;
        }
        int candidateCommentNum = candidate.getCommentNum();
        int currentCommentNum = current.getCommentNum();
        if (candidateCommentNum > 0 && currentCommentNum > 0 && candidateCommentNum != currentCommentNum) {
            return candidateCommentNum > currentCommentNum;
        }
        return candidateIndex > currentIndex;
    }

    private AssistantBotHistoryDTO createSkippedCommentHistory(PersonaProperties persona,
                                                               String boardTitle,
                                                               Integer targetPostNum,
                                                               String reason) {
        return createSkippedAutoPublishHistory(persona, boardTitle, MODE_COMMENT, targetPostNum, reason);
    }

    private AssistantBotHistoryDTO createSkippedAutoPublishHistory(PersonaProperties persona,
                                                                   String boardTitle,
                                                                   String mode,
                                                                   Integer targetPostNum,
                                                                   String reason) {
        AssistantBotHistoryDTO history = new AssistantBotHistoryDTO();
        history.setPersonaName(persona.getName());
        history.setBoardTitle(boardTitle);
        history.setGenerationMode(mode);
        history.setTargetPostNum(targetPostNum);
        history.setTopic("skip");
        history.setDraftTitle(null);
        history.setDraftBody("");
        if (MODE_POST.equals(normalizeMode(mode))) {
            history.setRawJson("{\"analysis\":{\"topic\":\"skip\",\"post_strategy\":\"fresh\",\"risk_notes\":[\"" + safeText(reason, 80) + "\"]},"
                    + "\"post\":{\"title\":\"\",\"body\":\"\"},"
                    + "\"self_review\":{\"naturalness\":100,\"novelty\":100,\"engagement\":0,\"needs_revision\":false}}");
        } else {
            history.setRawJson("{\"analysis\":{\"comment_type\":\"skip\",\"tone_match\":\"\",\"risk_notes\":[\"" + safeText(reason, 80) + "\"]},"
                    + "\"reply\":{\"should_reply\":false,\"body\":\"\"},"
                    + "\"self_review\":{\"naturalness\":100,\"context_fit\":100,\"conflict_risk\":0,\"needs_revision\":false}}");
        }
        history.setStatus(STATUS_SKIPPED);
        return history;
    }

    private void insertAutoPublishSkippedHistory(PersonaProperties persona,
                                                 String boardTitle,
                                                 String mode,
                                                 Integer targetPostNum,
                                                 String reason) {
        AssistantBotHistoryDTO history = createSkippedAutoPublishHistory(persona, boardTitle, mode, targetPostNum, reason);
        assistantBotMapper.insertHistory(history);
    }

    private void updateAutoPublishHistoryAsSkipped(Long historyId) {
        if (historyId == null) {
            return;
        }
        assistantBotMapper.updateStatus(historyId, STATUS_SKIPPED, null);
    }

    private DuplicateCheck findDuplicateIssue(String mode,
                                              String title,
                                              String body,
                                              List<BoardDTO> recentPosts,
                                              List<CommentDTO> recentComments,
                                              List<AssistantBotHistoryDTO> recentHistory) {
        double threshold = clampThreshold(botProperties.getDuplicateSimilarityThreshold());
        String normalizedTitle = normalizeText(title);
        String normalizedBody = normalizeText(body);

        for (AssistantBotHistoryDTO history : recentHistory) {
            if (history == null) {
                continue;
            }
            String historyMode = normalizeMode(history.getGenerationMode());
            if (MODE_POST.equals(mode) && MODE_POST.equals(historyMode)) {
                if (hasRepeatedTitleOpening(title, history.getDraftTitle())) {
                    return DuplicateCheck.of("최근 봇 게시글 제목 첫머리 패턴이 겹칩니다.");
                }
                if (isTooSimilar(normalizedTitle, normalizeText(history.getDraftTitle()), threshold)) {
                    return DuplicateCheck.of("최근 봇 게시글 제목과 너무 비슷합니다.");
                }
                if (isTooSimilar(normalizedBody, normalizeText(history.getDraftBody()), threshold)) {
                    return DuplicateCheck.of("최근 봇 게시글 본문과 너무 비슷합니다.");
                }
            }
            if (MODE_COMMENT.equals(mode) && MODE_COMMENT.equals(historyMode)) {
                if (isTooSimilar(normalizedBody, normalizeText(history.getDraftBody()), threshold)) {
                    return DuplicateCheck.of("최근 봇 댓글과 너무 비슷합니다.");
                }
            }
        }

        if (MODE_POST.equals(mode)) {
            for (BoardDTO post : recentPosts) {
                if (post == null) {
                    continue;
                }
                if (hasRepeatedTitleOpening(title, post.getTitle())) {
                    return DuplicateCheck.of("최근 게시글 제목 첫머리 패턴이 겹칩니다.");
                }
                if (isTooSimilar(normalizedTitle, normalizeText(post.getTitle()), threshold)) {
                    return DuplicateCheck.of("최근 게시글 제목과 너무 비슷합니다.");
                }
                if (isTooSimilar(normalizedBody, normalizeText(stripHtml(post.getContent())), Math.min(0.82, threshold + 0.08))) {
                    return DuplicateCheck.of("최근 게시글 본문 전개와 너무 비슷합니다.");
                }
            }
        } else {
            for (CommentDTO comment : recentComments) {
                if (comment == null) {
                    continue;
                }
                if (isTooSimilar(normalizedBody, normalizeText(stripHtml(comment.getContent())), threshold)) {
                    return DuplicateCheck.of("최근 댓글과 너무 비슷합니다.");
                }
            }
        }
        return DuplicateCheck.notDuplicate();
    }

    private JsonNode parseJson(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String cleaned = raw.trim()
                .replace("```json", "")
                .replace("```JSON", "")
                .replace("```", "")
                .trim();
        try {
            return objectMapper.readTree(cleaned);
        } catch (Exception ignored) {
            String extracted = extractFirstJsonObject(cleaned);
            if (!StringUtils.hasText(extracted)) {
                return null;
            }
            try {
                return objectMapper.readTree(extracted);
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    private String extractFirstJsonObject(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        int start = raw.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return raw.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private String extractGeneratedTitle(String mode, JsonNode result) {
        if (MODE_POST.equals(mode)) {
            return textOrNull(result.path("post").path("title"));
        }
        return null;
    }

    private String extractGeneratedBody(String mode, JsonNode result) {
        if (MODE_POST.equals(mode)) {
            return textOrNull(result.path("post").path("body"));
        }
        return textOrNull(result.path("reply").path("body"));
    }

    private String extractTopic(String mode, JsonNode result) {
        if (MODE_POST.equals(mode)) {
            return textOrNull(result.path("analysis").path("topic"));
        }
        return textOrNull(result.path("analysis").path("comment_type"));
    }

    private String resolvePersonaScopeIssue(PersonaProperties persona, String title, String body) {
        if (!isRaceGamePersona(persona)) {
            return null;
        }

        String combined = (safeText(title, 240) + " " + safeText(body, 600)).toLowerCase(Locale.ROOT);
        if (!containsGameTalkKeyword(combined)) {
            return persona.getName() + "은 스타크래프트 게임 이야기만 해야 합니다.";
        }
        if (!containsOwnRaceKeyword(persona, combined)) {
            return persona.getName() + "은 " + resolveRaceLabel(persona) + " 중심 단서를 반드시 포함해야 합니다.";
        }
        if (containsLifestyleOnlyKeyword(combined)) {
            return persona.getName() + "은 일상글이나 일반 잡담을 쓰면 안 됩니다.";
        }
        return null;
    }

    private String extractPostStrategy(String mode, JsonNode result) {
        if (!MODE_POST.equals(mode) || result == null || result.isMissingNode() || result.isNull()) {
            return null;
        }
        return normalizePostStrategy(textOrNull(result.path("analysis").path("post_strategy")));
    }

    private boolean resolveShouldReply(String mode, JsonNode result) {
        if (!MODE_COMMENT.equals(normalizeMode(mode))) {
            return true;
        }
        JsonNode shouldReply = result.path("reply").path("should_reply");
        return shouldReply.isMissingNode() || shouldReply.isNull() || shouldReply.asBoolean(true);
    }

    private KeywordOverlapCheck findRecentTitleKeywordIssue(String mode,
                                                            String title,
                                                            List<BoardDTO> recentPosts,
                                                            JsonNode result) {
        if (!MODE_POST.equals(mode) || !StringUtils.hasText(title) || recentPosts == null || recentPosts.isEmpty()) {
            return KeywordOverlapCheck.valid();
        }

        String strategy = extractPostStrategy(mode, result);
        if (!StringUtils.hasText(strategy)) {
            strategy = inferPostStrategy(title, recentPosts);
        }

        Set<String> generatedKeywords = extractTitleKeywords(title);
        if (generatedKeywords.isEmpty()) {
            return KeywordOverlapCheck.valid();
        }

        Set<String> latestKeywords = extractTitleKeywords(recentPosts.get(0) == null ? null : recentPosts.get(0).getTitle());
        int latestOverlap = countOverlap(generatedKeywords, latestKeywords);
        int maxOverlapWithRecentTitle = 0;
        for (BoardDTO post : recentPosts) {
            if (post == null) {
                continue;
            }
            maxOverlapWithRecentTitle = Math.max(maxOverlapWithRecentTitle,
                    countOverlap(generatedKeywords, extractTitleKeywords(post.getTitle())));
        }

        if (POST_STRATEGY_LINKED.equals(strategy)) {
            if (latestOverlap <= 0) {
                return KeywordOverlapCheck.of("연계 글인데 최신글 제목과 이어지는 핵심 단어가 없습니다.");
            }
            if (latestOverlap >= LINKED_TITLE_KEYWORD_OVERLAP_LIMIT
                    || maxOverlapWithRecentTitle >= LINKED_TITLE_KEYWORD_OVERLAP_LIMIT) {
                return KeywordOverlapCheck.of("연계 글인데 최신글 제목 단어를 너무 많이 재사용했습니다.");
            }
            return KeywordOverlapCheck.valid();
        }

        Set<String> hotKeywords = collectFrequentRecentTitleKeywords(recentPosts, 8);
        int hotOverlap = countOverlap(generatedKeywords, hotKeywords);
        if (hotOverlap >= FRESH_TITLE_KEYWORD_OVERLAP_LIMIT
                || maxOverlapWithRecentTitle >= FRESH_TITLE_KEYWORD_OVERLAP_LIMIT) {
            return KeywordOverlapCheck.of("신규 글인데 최신글 제목 단어를 너무 많이 재사용했습니다.");
        }
        return KeywordOverlapCheck.valid();
    }

    private String findBlockedWord(String... texts) {
        return BlockedWordMatcher.findBlockedWord(assistantProperties.getBlockedWords(), texts);
    }

    private String truncatePrompt(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            return "";
        }
        int maxPromptChars = Math.max(2000, botProperties.getMaxPromptChars());
        if (prompt.length() <= maxPromptChars) {
            return prompt;
        }
        return prompt.substring(0, maxPromptChars);
    }

    private PersonaProperties resolvePersona(String personaName) {
        return botProperties.resolvePersona(personaName);
    }

    private String resolveBoardTitle(String boardTitle, PersonaProperties persona) {
        String normalized = normalizeBoardTitle(StringUtils.hasText(boardTitle) ? boardTitle : persona == null ? null : persona.getBoardTitle());
        String configured = normalizeBoardTitle(persona == null ? null : persona.getBoardTitle());
        if (!StringUtils.hasText(normalized) || !Objects.equals(normalized, configured)) {
            return null;
        }
        return normalized;
    }

    private String resolveModel(PersonaProperties persona) {
        if (persona != null && StringUtils.hasText(persona.getModel())) {
            return persona.getModel();
        }
        return botProperties.getModel();
    }

    private boolean isTooSimilar(String left, String right, double threshold) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return false;
        }
        if (left.equals(right)) {
            return true;
        }
        if (left.length() >= 8 && right.contains(left)) {
            return true;
        }
        if (right.length() >= 8 && left.contains(right)) {
            return true;
        }
        return trigramSimilarity(left, right) >= threshold;
    }

    private boolean hasRepeatedTitleOpening(String leftTitle, String rightTitle) {
        String left = normalizeTitleForOpeningCompare(leftTitle);
        String right = normalizeTitleForOpeningCompare(rightTitle);
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return false;
        }

        String leftTokenPrefix = buildLeadingTokenKey(left, 2);
        String rightTokenPrefix = buildLeadingTokenKey(right, 2);
        if (StringUtils.hasText(leftTokenPrefix)
                && Objects.equals(leftTokenPrefix, rightTokenPrefix)
                && normalizeText(leftTokenPrefix).length() >= 4) {
            return true;
        }

        return sharedPrefixLength(normalizeText(left), normalizeText(right)) >= 6;
    }

    private double trigramSimilarity(String left, String right) {
        Set<String> leftSet = buildNgrams(left, 3);
        Set<String> rightSet = buildNgrams(right, 3);
        if (leftSet.isEmpty() || rightSet.isEmpty()) {
            return 0.0;
        }
        int intersection = 0;
        for (String item : leftSet) {
            if (rightSet.contains(item)) {
                intersection++;
            }
        }
        int union = leftSet.size() + rightSet.size() - intersection;
        if (union <= 0) {
            return 0.0;
        }
        return (double) intersection / union;
    }

    private Set<String> buildNgrams(String text, int size) {
        Set<String> grams = new LinkedHashSet<>();
        if (!StringUtils.hasText(text)) {
            return grams;
        }
        if (text.length() <= size) {
            grams.add(text);
            return grams;
        }
        for (int i = 0; i <= text.length() - size; i++) {
            grams.add(text.substring(i, i + size));
        }
        return grams;
    }

    private BoardDTO findPublishedPost(String boardTitle, String title, String writer) throws Exception {
        List<BoardDTO> latest = safeList(boardMapper.selectRecentPostsForBot(boardTitle, 5));
        for (BoardDTO post : latest) {
            if (post == null) {
                continue;
            }
            if (Objects.equals(title, post.getTitle()) && Objects.equals(writer, post.getWriter())) {
                return post;
            }
        }
        return null;
    }

    private String safeTitleForPublish(PersonaProperties persona, String title) {
        String normalized = normalizePublishText(persona, title);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return HtmlUtils.htmlEscape(normalized);
    }

    private String toHtmlBody(PersonaProperties persona, String body) {
        String normalized = normalizePublishText(persona, body);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String escaped = HtmlUtils.htmlEscape(formatBodyForPublish(normalized,
                sentenceLimitForPersona(persona, false),
                sentenceSeparatorForPersona(persona, false)));
        return escaped.replace("\r\n", "\n").replace('\r', '\n').replace("\n", "<br>");
    }

    private String safeCommentForPublish(PersonaProperties persona, String body) {
        String normalized = normalizePublishText(persona, body);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return formatBodyForPublish(normalized, sentenceLimitForPersona(persona, true));
    }

    private String formatBodyForPublish(String body) {
        return formatBodyForPublish(body, 0);
    }

    private String formatBodyForPublish(String body, int maxSentences) {
        return formatBodyForPublish(body, maxSentences, "\n");
    }

    private String formatBodyForPublish(String body, int maxSentences, String sentenceSeparator) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        String normalized = body.trim()
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t]+", " ");
        List<String> sentences = splitSentences(normalized);
        if (maxSentences > 0 && sentences.size() > maxSentences) {
            sentences = sentences.subList(0, maxSentences);
        }
        if (sentences.isEmpty()) {
            return normalized;
        }
        String separator = sentenceSeparator == null ? "\n" : sentenceSeparator;
        return String.join(separator, sentences).trim();
    }

    private String normalizePublishText(PersonaProperties persona, String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String normalized = text.trim()
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t]+", " ");
        if (isRepetitiveByDesignPersona(persona)) {
            return normalizeMeowPublishText(normalized);
        }
        return isSingleSentenceOnlyPersona(persona) ? firstSentenceOnly(normalized) : normalized;
    }

    private int sentenceLimitForPersona(PersonaProperties persona, boolean commentMode) {
        if (persona == null || !StringUtils.hasText(persona.getName())) {
            return 0;
        }
        String name = persona.getName();
        if ("야옹봇".equals(name)) {
            return 3;
        }
        if ("건강봇".equals(name)) {
            return commentMode ? 2 : 5;
        }
        if ("저묵묵봇".equals(name)) {
            return 1;
        }
        if ("테뻔뻔봇".equals(name) || "프징징봇".equals(name)) {
            return commentMode ? 2 : 5;
        }
        return 0;
    }

    private String sentenceSeparatorForPersona(PersonaProperties persona, boolean commentMode) {
        if (!commentMode && persona != null && "건강봇".equals(persona.getName())) {
            return "\n\n\n";
        }
        return "\n";
    }

    private boolean isSingleSentenceOnlyPersona(PersonaProperties persona) {
        return persona != null && "저묵묵봇".equals(persona.getName());
    }

    private List<String> splitSentences(String text) {
        if (!StringUtils.hasText(text)) {
            return Collections.emptyList();
        }
        String normalized = text.trim()
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        List<String> sentences = new ArrayList<>();
        String[] lines = normalized.split("\\n+");
        for (String line : lines) {
            if (!StringUtils.hasText(line)) {
                continue;
            }
            String[] parts = line.trim().split("(?<=[.!?。！？])\\s+");
            for (String part : parts) {
                if (StringUtils.hasText(part)) {
                    sentences.add(part.trim());
                }
            }
        }
        if (sentences.isEmpty() && StringUtils.hasText(normalized)) {
            sentences.add(normalized.replace('\n', ' ').trim());
        }
        return sentences;
    }

    private String normalizeMeowPublishText(String text) {
        List<String> meowSegments = new ArrayList<>();
        for (String segment : splitMeowSegments(text)) {
            if (isMeowOnlySegment(segment)) {
                meowSegments.add(segment.trim().replaceAll("\\s+", " "));
            }
            if (meowSegments.size() >= 3) {
                break;
            }
        }
        if (meowSegments.isEmpty()) {
            return "야옹";
        }
        return String.join("\n", meowSegments);
    }

    private List<String> splitMeowSegments(String text) {
        if (!StringUtils.hasText(text)) {
            return Collections.emptyList();
        }
        String normalized = text.trim()
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        String[] parts = normalized.split("(?<=[.!?。！？])\\s+|\\n+");
        List<String> segments = new ArrayList<>();
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                segments.add(part.trim());
            }
        }
        return segments;
    }

    private boolean isMeowOnlySegment(String segment) {
        if (!StringUtils.hasText(segment)) {
            return false;
        }
        String core = segment.replaceAll("[\\s~.!?。！？]+", "");
        return StringUtils.hasText(core) && core.matches("[야옹]+");
    }

    private String firstSentenceOnly(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        List<String> sentences = splitSentences(text);
        if (!sentences.isEmpty()) {
            return sentences.get(0);
        }
        return text.trim();
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String text = node.asText(null);
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private static void appendLine(StringBuilder sb, String line) {
        sb.append(line).append('\n');
    }

    private static String stripHtml(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        return html.replaceAll("(?is)<br\\s*/?>", "\n")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String safeText(String text, int maxChars) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, Math.max(1, maxChars)) + "...";
    }

    private static int resolveLimit(Integer override, int defaultValue) {
        if (override != null && override > 0) {
            return Math.min(override, 50);
        }
        return Math.min(Math.max(1, defaultValue), 50);
    }

    private static String normalizeBoardTitle(String boardTitle) {
        if (!StringUtils.hasText(boardTitle)) {
            return null;
        }
        return boardTitle.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeMode(String mode) {
        if (!StringUtils.hasText(mode)) {
            return null;
        }
        return mode.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String lowered = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        return NON_TEXT_PATTERN.matcher(lowered).replaceAll("");
    }

    private static String normalizeTitleForOpeningCompare(String title) {
        if (!StringUtils.hasText(title)) {
            return "";
        }
        String trimmed = TITLE_COMMENT_COUNT_PATTERN.matcher(title.trim()).replaceAll("");
        return trimmed.replaceAll("\\s+", " ").trim();
    }

    private static String normalizePostStrategy(String strategy) {
        if (!StringUtils.hasText(strategy)) {
            return null;
        }
        String normalized = strategy.trim().toLowerCase(Locale.ROOT);
        if ("linked".equals(normalized) || "연계".equals(normalized) || "연계글".equals(normalized)) {
            return POST_STRATEGY_LINKED;
        }
        if ("fresh".equals(normalized) || "new".equals(normalized) || "신규".equals(normalized) || "새주제".equals(normalized)) {
            return POST_STRATEGY_FRESH;
        }
        return null;
    }

    private static double clampThreshold(double threshold) {
        return Math.max(0.45, Math.min(threshold, 0.95));
    }

    private static double clampProbability(double probability) {
        return Math.max(0.0, Math.min(probability, 0.99));
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private String recommendPostTopicLane(List<AssistantBotHistoryDTO> recentHistory, int attempt) {
        List<String> lanes = new ArrayList<>();
        lanes.add(TOPIC_LANE_STAR_CHAT);
        lanes.add(TOPIC_LANE_DAILY_LIFE);
        lanes.add(TOPIC_LANE_LIGHT_CHAT);
        lanes.add(TOPIC_LANE_WHIRING);

        int[] counts = countRecentPostTopicLanes(recentHistory);
        int minCount = Integer.MAX_VALUE;
        for (int count : counts) {
            minCount = Math.min(minCount, count);
        }

        List<String> candidates = new ArrayList<>();
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] == minCount) {
                candidates.add(lanes.get(i));
            }
        }

        if (candidates.isEmpty()) {
            return TOPIC_LANE_STAR_CHAT;
        }

        int safeAttempt = Math.max(1, attempt);
        int offset = (int) Math.floorMod(LocalDate.now(clock).toEpochDay() + safeAttempt, (long) candidates.size());
        return candidates.get(offset);
    }

    private String buildTopicBalanceHint(PersonaProperties persona, List<AssistantBotHistoryDTO> recentHistory) {
        if (isRepetitiveByDesignPersona(persona)) {
            return "야옹봇은 주제 균형을 적용하지 않는다. 최근 글과 다르게 보이게 하려고 설명어나 사람 말투를 섞지 말고 울음의 길이와 띄어쓰기만 살짝 바꾼다.";
        }
        if (isHealthPersona(persona)) {
            return "최근 건강봇 게시글과 같은 건강 주제, 제목, 본문 전개를 반복하지 말고 새 건강관리 상식을 고른다.";
        }
        if (isRaceGamePersona(persona)) {
            return "최근 " + persona.getName() + " 게시글과 같은 빌드/유닛/상성 이야기를 반복하지 말고, "
                    + resolveRaceLabel(persona) + " 중심의 다른 스타크래프트 게임 화제를 고른다.";
        }

        int[] counts = countRecentPostTopicLanes(recentHistory);
        return String.format(Locale.ROOT,
                "최근 %s 게시글 분포는 스타수다 %d, 일상글 %d, 잡담/뻘글 %d, 밸런스징징 %d 이다. 가장 적은 축을 우선 보강하라.",
                persona == null ? "봇" : persona.getName(), counts[0], counts[1], counts[2], counts[3]);
    }

    private void appendPersonaGoalRules(StringBuilder sb, PersonaProperties persona) {
        sb.append("목표:\n");
        if (isRepetitiveByDesignPersona(persona)) {
            sb.append("- 야옹봇의 목표는 의미 있는 대화나 분석이 아니라 울음 컨셉을 흔들림 없이 유지하는 것이다.\n");
            sb.append("- 최근 글, 스타크래프트, 일상, 건강, 밸런스 징징 같은 주제로 확장하지 않는다.\n");
            sb.append("- 제목, 본문, 댓글 모두 야옹 계열 울음만 쓰고 설명어, 사람 말투, 감정 해설을 섞지 않는다.\n\n");
            return;
        }
        if (isHealthPersona(persona)) {
            sb.append("- 건강봇은 생활 속 건강 상식 도움말을 쓰는 커뮤니티 유저다.\n");
            sb.append("- 매 게시글은 한 가지 건강관리 지식을 다루고, '핵심 상식 -> 이유 -> 생활 속 적용 -> 주의/상담 기준' 흐름을 갖춘다.\n");
            sb.append("- 심호흡, 차 한잔, 물 마시기처럼 누구나 아는 한 줄 조언만으로 끝내면 실패다.\n");
            sb.append("- 스타크래프트 잡담, 종족 징징, 일반 일상글로 컨셉을 옮기지 않는다.\n");
            sb.append("- 의학적 표현은 일반 정보 수준으로만 쓰고, 개인의 증상을 진단하거나 치료법을 확정하지 않는다.\n\n");
            return;
        }

        sb.append("- 실제 커뮤니티 상주 유저처럼 자연스럽고 재밌는 글을 쓴다.\n");
        if (hasPersonaName(persona, "테뻔뻔봇")) {
            sb.append("- 테뻔뻔봇은 테란 중심의 스타크래프트 게임 이야기만 한다. 일상글, 일반 잡담, 건강, 날씨, 식사 같은 소재로 빠지지 않는다.\n");
            sb.append("- 모든 글과 댓글은 테란 유저 시점에서 빌드, 운영, 유닛, 상성, 맵, 래더, 경기 흐름을 얄밉게 해석한다.\n");
        } else if (hasPersonaName(persona, "저묵묵봇")) {
            sb.append("- 저묵묵봇은 저그 중심의 스타크래프트 게임 이야기만 한다. 일상글, 일반 잡담, 건강, 날씨, 식사 같은 소재로 빠지지 않는다.\n");
            sb.append("- 모든 글과 댓글은 저그 유저 시점에서 운영, 유닛, 상성, 맵, 래더, 경기 흐름을 짧고 무뚝뚝하게 해석한다.\n");
        } else if (hasPersonaName(persona, "훈훈봇")) {
            sb.append("- 훈훈봇 캐릭터성은 따뜻한 커뮤니티 반응이다. 분쟁이나 시비보다 부담 없는 응원, 소소한 관찰, 가벼운 재치를 우선한다.\n");
        } else {
            sb.append("- ").append(persona.getName()).append("은 프로토스 중심의 스타크래프트 게임 이야기만 한다. 일상글, 일반 잡담, 건강, 날씨, 식사 같은 소재로 빠지지 않는다.\n");
            sb.append("- 모든 글과 댓글은 프로토스 유저 시점에서 빌드, 운영, 유닛, 상성, 맵, 래더, 경기 흐름을 억울하게 해석한다.\n");
            sb.append("- 밸런스 징징은 여러 결 중 하나일 뿐이며, 매번 기본값처럼 선택하지 않는다.\n");
        }
        if (isRaceGamePersona(persona)) {
            sb.append("- 사이트 밈과 최근 화제를 쓰더라도 스타크래프트 게임 맥락인 경우만 반영한다.\n\n");
            return;
        }
        sb.append("- 사이트 밈, 최근 화제, 자주 보이는 표현을 반영한다.\n\n");
    }

    private void appendPostTopicGuide(StringBuilder sb,
                                      PersonaProperties persona,
                                      String recommendedTopicLane,
                                      String recommendedPostStrategy) {
        sb.append("이번 게시글 주제 가이드:\n");
        if (isRepetitiveByDesignPersona(persona)) {
            sb.append("- 주제 선택보다 야옹봇 형식 규칙이 최우선이다.\n");
            sb.append("- 야옹봇은 주제 균형을 적용하지 않는다.\n");
            sb.append("- analysis.topic 은 '야옹', analysis.post_strategy 는 fresh 로 둬도 된다.\n");
            sb.append("- 최신글과 연계하려고 단어를 빌리거나 설명을 붙이지 않는다.\n\n");
            return;
        }
        if (isHealthPersona(persona)) {
            sb.append("- 이번 추천 작성 전략: ").append(POST_STRATEGY_LINKED.equals(recommendedPostStrategy) ? "연계 글" : "신규 글").append("\n");
            sb.append("- 스타 수다, 일반 잡담, 밸런스징징 순환 규칙은 건강봇에 적용하지 않는다.\n");
            sb.append("- 수면 위생, 장시간 앉아 있을 때의 허리/목 관리, 눈 피로, 가벼운 운동, 식사 균형, 카페인, 혈압/혈당 관리, 손 씻기, 계절별 컨디션 관리, 건강검진 같은 주제를 돌려 쓴다.\n");
            sb.append("- 최신글과 연계하더라도 건강 상식으로 연결하고, 전문 용어를 쓰면 쉬운 말로 풀어준다.\n\n");
            return;
        }
        if (isRaceGamePersona(persona)) {
            sb.append("- 이번 추천 작성 전략: ").append(POST_STRATEGY_LINKED.equals(recommendedPostStrategy) ? "연계 글" : "신규 글").append("\n");
            sb.append("- 주제 풀은 스타크래프트 게임 이야기로만 제한한다. 일상글, 일반 잡담, 뻘글, 건강 조언은 금지다.\n");
            sb.append("- 반드시 ").append(resolveRaceLabel(persona)).append(" 관점과 ").append(resolveRaceKeywordHint(persona)).append(" 같은 자기 종족 단서를 제목이나 본문에 넣는다.\n");
            sb.append("- 다룰 수 있는 축은 빌드, 운영, 유닛 조합, 상성 체감, 맵, 래더, 경기 관전평, 밸런스 체감이다.\n");
            sb.append("- 최신글과 연계하더라도 일상 소재로 비틀지 말고 스타크래프트 게임 흐름 안에서만 이어받는다.\n\n");
            return;
        }

        sb.append("- 우선 추천 주제 결: ").append(recommendedTopicLane).append("\n");
        sb.append("- 이번 추천 작성 전략: ").append(POST_STRATEGY_LINKED.equals(recommendedPostStrategy) ? "연계 글" : "신규 글").append("\n");
        sb.append("- 주제 풀은 '스타수다 / 일상글 / 잡담/뻘글 / 밸런스징징' 네 축으로 고르게 순환한다.\n");
        if (hasPersonaName(persona, "훈훈봇")) {
            sb.append("- 최근 훈훈봇 글이 스타 얘기 위주였다면 일상 소재를, 일상 소재 위주였다면 가벼운 스타 수다를 섞는다.\n");
            sb.append("- 일상글은 식사, 수면, 날씨, 출근/퇴근, 피곤함, 주말 계획처럼 가벼운 소재를 자연스럽게 쓴다.\n\n");
            return;
        }
        sb.append("- 최근 ").append(persona.getName()).append(" 글이 징징 위주였다면 이번에는 스타 썰, 게임하다 생긴 일, 그냥 하루 있었던 일 같은 방향을 우선한다.\n");
        sb.append("- 스타 관련 글은 전략 강의체보다 썰, 관전평, 추억, 래더 한탄 아닌 소소한 수다를 더 자주 섞는다.\n");
        sb.append("- 일상글은 식사, 수면, 날씨, 출근/퇴근, 피곤함, 주말 계획처럼 가벼운 소재도 허용한다.\n\n");
    }

    private void appendHumanStyleRules(StringBuilder sb, PersonaProperties persona) {
        sb.append("인간스러움 규칙:\n");
        if (isRepetitiveByDesignPersona(persona)) {
            sb.append("- 사람처럼 설명하거나 반응하지 않는다.\n");
            sb.append("- 최근 화제의 의미를 해석하지 말고 울음의 리듬만 짧게 변주한다.\n\n");
            return;
        }
        if (isHealthPersona(persona)) {
            sb.append("- 친근한 말투를 쓰되 정보형 도움말의 정확성과 절제를 우선한다.\n");
            sb.append("- 과장된 드립, 종족 밈, 밸런스 논쟁, AI 문체를 피한다.\n");
            sb.append("- 독자가 바로 적용할 수 있는 기준이나 예외를 한 가지 이상 넣는다.\n\n");
            return;
        }
        if (isRaceGamePersona(persona)) {
            sb.append("- 완벽하게 정제된 문장보다 스타 커뮤니티스러운 리듬을 우선한다.\n");
            sb.append("- 설명 과잉 금지.\n");
            sb.append("- 구체적 장면은 래더, 빌드, 교전, 견제, 운영, 맵, 경기 흐름 안에서만 잡는다.\n");
            sb.append("- 매번 같은 어미, 같은 농담, 같은 길이를 쓰지 않는다.\n");
            sb.append("- 진심 60 / 드립 40 정도의 결을 유지하되 일상 드립은 쓰지 않는다.\n\n");
            return;
        }
        sb.append("- 완벽하게 정제된 문장보다 커뮤니티스러운 리듬을 우선한다.\n");
        sb.append("- 설명 과잉 금지.\n");
        sb.append("- 구체적 장면이나 감정 한 조각을 넣는다.\n");
        sb.append("- 매번 같은 어미, 같은 농담, 같은 길이를 쓰지 않는다.\n");
        sb.append("- 진심 60 / 드립 40 정도의 결을 유지한다.\n\n");
    }

    private void appendFormatRules(StringBuilder sb, PersonaProperties persona) {
        sb.append("형식 규칙:\n");
        if (isRepetitiveByDesignPersona(persona)) {
            sb.append("- 제목, 본문, 댓글은 야옹 계열 울음 1~3줄만 사용한다.\n");
            sb.append("- 일반 문장, 설명, 해시태그, 이모지, 괄호 설명은 쓰지 않는다.\n\n");
            return;
        }
        if (isHealthPersona(persona)) {
            sb.append("- 게시글 본문은 3~5문장으로 쓴다.\n");
            sb.append("- 게시글 문장 사이에는 빈 줄이 두 줄 생기도록 줄바꿈을 두 번씩 넣는다.\n");
            sb.append("- 댓글은 1~2문장으로 짧게 쓴다.\n\n");
            return;
        }
        if (hasPersonaName(persona, "저묵묵봇")) {
            sb.append("- 제목, 본문, 댓글 모두 한 문장만 쓴다.\n");
            sb.append("- 줄바꿈을 쓰지 말고 짧고 건조하게 끝낸다.\n\n");
            return;
        }
        sb.append("- 게시글/댓글 본문이 두 문장 이상이면 한 문장마다 줄바꿈한다.\n");
        sb.append("- 문단을 길게 붙여 쓰지 말고, 문장 하나를 한 줄로 둔다.\n\n");
    }

    private void appendCommentFollowupRules(StringBuilder sb, PersonaProperties persona) {
        sb.append("- 최신 댓글이 자기 댓글이면 should_reply=false 로 두고 새 댓글을 더 쓰지 않는다.\n");
        sb.append("- 같은 글에 방금 이미 한마디 했는데 새 반응이 없으면 또 끼어들지 않는다.\n");
        if (isRepetitiveByDesignPersona(persona)) {
            sb.append("- 댓글 본문도 야옹 계열 울음만 쓰고 대상 글을 해석하거나 설명하지 않는다.\n");
            return;
        }
        if (isHealthPersona(persona)) {
            sb.append("- 댓글은 반드시 대상 글 제목/본문이나 기존 댓글의 상황을 건강관리 기준으로 연결한다.\n");
            sb.append("- 다른 봇 글에 댓글 달 때도 시비를 걸지 말고 건강 상식이나 주의 기준만 짧게 덧붙인다.\n");
            return;
        }
        if (hasPersonaName(persona, "훈훈봇")) {
            sb.append("- 댓글은 반드시 대상 글 제목/본문이나 기존 댓글의 핵심 감정을 직접 이어받는다.\n");
            sb.append("- 다른 봇 글에 댓글 달 때도 싸움을 키우지 말고 가볍게 웃긴 뒤 좋은 쪽으로 마무리한다.\n");
            return;
        }
        sb.append("- 댓글은 반드시 대상 글 제목/본문이나 기존 댓글 흐름 중 하나 이상을 직접 이어받아야 한다.\n");
        sb.append("- 최근 댓글 중 다른 유저나 다른 봇의 주장에 대해 동조 또는 반박을 분명히 선택한다.\n");
        sb.append("- 다른 봇 글에 댓글 달 때는 적당히 시비를 걸고, 너무 착하게 맞장구만 치지 않는다.\n");
        sb.append("- 특히 종족 징징이나 자랑글이면 자기 페르소나 관점을 유지하며 소소하게 받아친다.\n");
        sb.append("- 싸움을 키우기보다 커뮤니티식 티키타카 한두 마디로 끝내는 쪽을 선호한다.\n");
    }

    private void appendWorkProcedureRules(StringBuilder sb, PersonaProperties persona) {
        sb.append("\n작업 절차:\n");
        if (isRepetitiveByDesignPersona(persona)) {
            sb.append("1. 최근 게시글이나 댓글을 분석하지 않는다.\n");
            sb.append("2. 야옹 울음의 길이, 물결표, 띄어쓰기만 불규칙하게 고른다.\n");
            sb.append("3. 의미 있는 문장이나 설명 없이 JSON 형식에 맞춰 작성한다.\n\n");
            return;
        }
        if (isRaceGamePersona(persona)) {
            sb.append("1. 최근 게시판에서 스타크래프트 게임 관련 화제와 말투만 분석한다.\n");
            sb.append("2. ").append(resolveRaceLabel(persona)).append(" 중심의 빌드, 운영, 유닛, 상성, 맵, 래더, 경기 흐름 후보를 여러 개 만든다.\n");
            sb.append("3. 일상글, 일반 잡담, 건강, 날씨, 식사 같은 후보는 버린다.\n");
            sb.append("4. 최근 ").append(persona.getName()).append(" 글과 겹치지 않는 게임 화제를 고른다.\n");
            sb.append("5. 자기검수 때 게임 단서와 자기 종족 단서가 없으면 스스로 수정한다.\n\n");
            return;
        }
        sb.append("1. 최근 게시판의 밈, 말투, 화제, 금지해야 할 패턴을 분석한다.\n");
        sb.append("2. 현재 타이밍에 맞는 글감 후보를 여러 개 만든다.\n");
        sb.append("3. 최근 ").append(persona.getName()).append(" 글과 겹치지 않는 후보를 고른다.\n");
        sb.append("4. 자연스러운 제목/본문 또는 댓글을 작성한다.\n");
        sb.append("5. 자기검수 후, 어색하면 스스로 수정한다.\n\n");
    }

    private String buildPersonaPromptRule(PersonaProperties persona) {
        String name = persona == null ? "" : String.valueOf(persona.getName());
        if ("테뻔뻔봇".equals(name)) {
            return "테뻔뻔봇은 테란 유저 특유의 뻔뻔함과 잘난 척이 기본값이다. 스타크래프트 게임 이야기만 하며, 모든 글은 테란 빌드/운영/유닛/상성/맵/래더 체감에서 출발한다. '테란이 사기가 아니라 내가 잘하는 거다' 같은 태도로 말하고, 밸런스 얘기를 해도 테란 쪽을 더 좋게 쳐주며 자신감 있게 우긴다. 게시글은 1~5문장, 댓글은 1~2문장으로 쓴다.";
        }
        if ("저묵묵봇".equals(name)) {
            return "저묵묵봇은 말수 적은 경상도 아재 같은 톤이다. 스타크래프트 게임 이야기만 하며, 모든 글은 저그 빌드/운영/유닛/상성/맵/래더 체감에서 출발한다. 제목, 본문, 댓글 모두 한 문장만 쓰고, 징징도 길게 안 하고 한두마디 툭 던지듯 마무리한다. 사투리는 과장하지 말고 자연스럽게 살짝만 쓴다.";
        }
        if ("훈훈봇".equals(name)) {
            return "훈훈봇은 스타크래프트 얘기뿐 아니라 전혀 무관한 일상 소재도 자연스럽게 다루는 따뜻한 커뮤니티 유저다. 스타 수다, 일상글, 잡담, 댓글 반응을 골고루 쓰되 억지 감동문이나 설교체로 흐르지 말고, 상대가 기분 좋게 받아들일 만한 짧은 응원과 재치 있는 한마디를 섞는다. 출근길, 식사, 날씨, 잠, 집안일, 사소한 실수 같은 아무 일상 소재도 부담 없이 꺼낸다.";
        }
        if ("야옹봇".equals(name)) {
            return "야옹봇은 의미 있는 문장을 쓰지 않는다. 제목과 본문, 댓글 모두 '야옹', '야~~옹~~~~', '야옹 야~~옹~~~~' 같은 고양이 울음만 2~3차례 반복한다. 설명, 스타크래프트 분석, 사람 말투, 이모지, 해시태그, 괄호 설명은 절대 쓰지 않는다.";
        }
        if ("건강봇".equals(name)) {
            return "건강봇은 생활 속 건강상식을 짧은 도움말로 알려주는 커뮤니티 유저다. 매 글은 수면, 운동, 영양, 눈/목/허리, 혈압/혈당, 위생, 스트레스, 계절질환, 건강검진 같은 건강관리 주제 중 하나를 고르고, 왜 중요한지와 실천 기준, 주의할 예외를 함께 담는다. '심호흡해라', '차 한잔해라'처럼 뻔한 휴식 권유만으로 끝내지 말고, 독자가 새로 배울 만한 건강 상식을 설명한다. 의료 진단, 치료 확정, 약물 복용 지시는 하지 말고 증상이 심하거나 지속되면 전문가 상담을 권한다. 본문 문장 사이에는 줄바꿈을 두 번씩 넣는 요상한 스타일을 반드시 유지한다.";
        }
        return "프징징봇은 프로토스가 늘 손해 본다고 믿는 투덜이다. 스타크래프트 게임 이야기만 하며, 모든 글은 프로토스 빌드/운영/유닛/상성/맵/래더 체감에서 출발한다. 밸런스 얘기를 해도 과몰입 드립과 커뮤니티 감각을 같이 살린다. 게시글은 1~5문장, 댓글은 1~2문장으로 쓴다.";
    }

    private String buildPersonaPostStyleRule(PersonaProperties persona) {
        String name = persona == null ? "" : String.valueOf(persona.getName());
        if ("테뻔뻔봇".equals(name)) {
            return "게시글 제목은 테란 게임 이야기의 결론이나 허세를 먼저 던지는 쪽이 맞다. 일기체나 생활 잡담으로 풀지 말고, 테란 빌드/운영/유닛/상성에 대한 잘난 척이나 얄미운 단정으로 출발하라. 본문은 1~5문장, 댓글은 1~2문장으로 유지하라.";
        }
        if ("저묵묵봇".equals(name)) {
            return "게시글 제목은 짧고 툭 끊겨야 한다. 제목도 한 문장만 쓰고 생활 잡담 대신 저그 운영/유닛/상성에 대한 무뚝뚝한 한마디, 짧은 관찰, 건조한 투덜거림으로 시작하라.";
        }
        if ("훈훈봇".equals(name)) {
            return "게시글 제목은 스타크래프트 얘기 아니어도 된다. 부담스럽게 착한 말만 앞세우지 말고, 작은 관찰이나 전혀 무관한 일상 소재에서 자연스럽게 훈훈한 결론으로 이어지게 한다. 제목만 봐도 긍정적인 온도가 느껴지되 과장된 미담체는 피하라.";
        }
        if ("야옹봇".equals(name)) {
            return "게시글 제목도 본문도 오직 고양이 울음 반복이어야 한다. 제목 예시는 '야옹 야~~옹~~~~'처럼 짧게 쓰고, 본문은 같은 결의 울음을 줄바꿈으로 2~3번만 반복한다.";
        }
        if ("건강봇".equals(name)) {
            return "게시글 제목은 건강상식 하나를 정보형으로 바로 말한다. 본문은 3~5문장으로 쓰고, 첫 문장은 핵심 상식, 중간 문장은 이유나 생활 속 기준, 마지막 문장은 무리하면 안 되는 상황이나 전문가 상담 기준을 담는다. 각 문장 사이에 빈 줄이 두 줄 생기도록 줄바꿈을 두 번씩 넣는다.";
        }
        return "게시글 제목은 프로토스 게임 이야기의 억울함이나 과몰입 드립을 바로 드러내되, 매번 같은 징징 도입부로 풀지 마라. 생활 잡담으로 빠지지 말고 프로토스 빌드/운영/유닛/상성 단서를 제목에 담아라.";
    }

    private String recommendPostStrategy(PersonaProperties persona, List<BoardDTO> recentPosts, int attempt) {
        if (recentPosts == null || recentPosts.isEmpty()) {
            return POST_STRATEGY_FRESH;
        }
        int safeAttempt = Math.max(1, attempt);
        long seed = LocalDate.now(clock).toEpochDay() + safeAttempt;
        if (persona != null && StringUtils.hasText(persona.getName())) {
            seed += persona.getName().hashCode();
        }
        return Math.floorMod(seed, 2L) == 0 ? POST_STRATEGY_LINKED : POST_STRATEGY_FRESH;
    }

    private String buildCommentInteractionRule(PersonaProperties persona, BoardDTO targetPost) {
        String personaName = persona == null ? "" : String.valueOf(persona.getName());
        String writer = targetPost == null ? "" : safeText(targetPost.getWriter(), 60);
        String titleAndBody = ((targetPost == null ? "" : safeText(targetPost.getTitle(), 120)) + " "
                + (targetPost == null ? "" : safeText(stripHtml(targetPost.getContent()), 220))).toLowerCase(Locale.ROOT);

        boolean targetIsBot = isKnownBotName(writer);
        boolean protossWhine = containsAny(titleAndBody, "프로토스", "토스", "프사기", "억까", "불리", "서럽", "힘들", "사기");
        boolean terranBrag = containsAny(titleAndBody, "테란", "테사기", "잘해서", "실력", "운영", "개잘", "뻔뻔");
        boolean gameTalk = containsAny(titleAndBody, "저그", "테란", "프로토스", "토스", "스타", "래더", "빌드", "운영", "뮤탈", "히드라", "럴커", "저글링", "디파일러", "배슬", "탱크", "리버", "드라군", "질럿");

        if ("테뻔뻔봇".equals(personaName)) {
            if (!gameTalk) {
                return "대상 글이 게임 이야기가 아니어도 생활 소재에 맞장구치지 말고, 테란 빌드/운영/유닛/상성 중 하나로 짧게 테란식 게임 한마디만 던져라.";
            }
            if (targetIsBot || protossWhine) {
                return writer + " 글에는 쉽게 공감하지 말고, '아니거든? 테란이 더 잘해서 그런 거거든?' 같은 결로 가볍게 받아쳐라. 테란 억까 서사는 반박하고 자기 실력 자랑을 살짝 섞어라.";
            }
            return "기본적으로 자신만만하고 약간 얄미운 톤으로 말하되, 테란 쪽이 손해라는 식이면 더 노골적으로 받아쳐라.";
        }
        if ("저묵묵봇".equals(personaName)) {
            if (!gameTalk) {
                return "대상 글이 게임 이야기가 아니어도 생활 소재에 맞장구치지 말고, 저그 운영/유닛/상성 중 하나로 짧고 무뚝뚝한 게임 한마디만 던져라.";
            }
            if (gameTalk) {
                if (targetIsBot || terranBrag || protossWhine) {
                    return writer + " 글에는 저그 유저 시점으로 짧고 무뚝뚝하게 툭 받아쳐라. 댓글도 한 문장만 쓰고, 뮤탈, 히드라, 저글링, 운영 말리는 체감 같은 저그 쪽 기준을 깔되 길게 설명하지 말고 경상도 느낌 한마디로 묵직하게 시비를 건다.";
                }
                return "게임 얘기면 저그 유저 시점으로 한두 마디만 던져라. 댓글도 한 문장만 쓰고, 뮤탈, 히드라, 저글링, 운영 말리는 체감처럼 저그 쪽 기준을 깔되 괜히 과하게 친절하게 풀어주지 않는다.";
            }
            return "짧고 무뚝뚝한 경상도 톤으로 한두 마디만 던져라. 댓글도 한 문장만 쓰고, 괜히 과하게 친절하게 풀어주지 않는다.";
        }
        if ("훈훈봇".equals(personaName)) {
            if (targetIsBot) {
                return writer + " 글의 캐릭터성을 받아주되 싸움을 키우지 말고, 가볍게 웃긴 뒤 좋은 쪽으로 마무리하는 덕담 댓글을 쓴다. 과한 칭찬보다 상황에 맞는 응원 한마디가 중심이다.";
            }
            return "상대 글의 핵심 감정이나 상황을 먼저 받아주고, 게임 얘기든 전혀 무관한 일상이든 자연스럽게 맞장구치며 부담스럽지 않은 응원이나 긍정적인 덕담으로 짧게 마무리한다. 설교, 과장된 감동, 지나친 친한 척은 피한다.";
        }
        if ("야옹봇".equals(personaName)) {
            return "댓글도 오직 '야옹', '야~~옹~~~~', '야옹 야~~옹~~~~' 같은 고양이 울음만 2~3차례 반복한다. 상대 글에 대한 해석이나 설명을 붙이지 않는다.";
        }
        if ("건강봇".equals(personaName)) {
            return "상대 글의 상황에 맞춰 짧은 건강상식이나 관리 기준을 덧붙인다. 댓글은 1~2문장으로 쓰되 단순 위로나 '쉬어라' 수준에서 끝내지 말고, 왜 도움이 되는지 한 가지 근거를 붙인다. 진단이나 치료 지시처럼 단정하지 말고 증상이 심하거나 오래가면 전문가 상담을 권한다.";
        }
        if (targetIsBot && "테뻔뻔봇".equals(writer)) {
            return "테뻔뻔봇이 잘난 척하면 곧이곧대로 받아주지 말고, 프로토스 입장에서 억울함 섞인 농담으로 받아쳐라.";
        }
        if (!gameTalk) {
            return "대상 글이 게임 이야기가 아니어도 생활 소재에 맞장구치지 말고, 프로토스 빌드/운영/유닛/상성 중 하나로 억울한 게임 한마디만 던져라.";
        }
        return "상대 글이 다른 봇 글이면 너무 순하게 동조하지 말고, 프로토스 관점과 프징징봇 캐릭터성으로 한마디 받아쳐라. 프징징봇은 게시글 1~5문장, 댓글 1~2문장으로 쓴다.";
    }

    private String buildCommentThreadHint(PersonaProperties persona, BoardDTO targetPost, List<CommentDTO> recentComments) {
        CommentDTO latestComment = findLatestComment(recentComments);
        if (latestComment == null) {
            return "아직 댓글이 거의 없으니 글 제목/본문을 직접 이어받아 첫 반응을 만든다.";
        }

        if (isBotIdentity(latestComment.getNickname(), persona)) {
            return "최신 댓글이 이미 " + persona.getName() + " 본인 것이다. 새 댓글을 더 달지 말고 should_reply=false 로 끝낸다.";
        }

        String latestNickname = StringUtils.hasText(latestComment.getNickname()) ? latestComment.getNickname() : "익명";
        String latestSummary = "[" + latestNickname + "] " + safeText(stripHtml(latestComment.getContent()), 80);
        CommentDTO latestOtherComment = findLatestOtherComment(recentComments, persona);
        if (latestOtherComment == null) {
            return "최신 댓글 흐름은 " + latestSummary + " 이다. 대상 글 제목/본문과 이 댓글을 묶어서 짧게 반응한다.";
        }

        String latestOtherNickname = StringUtils.hasText(latestOtherComment.getNickname()) ? latestOtherComment.getNickname() : "익명";
        String latestOtherSummary = "[" + latestOtherNickname + "] " + safeText(stripHtml(latestOtherComment.getContent()), 80);
        if (latestComment == latestOtherComment) {
            return "최신 댓글 흐름은 " + latestSummary + " 이다. 이 주장에 동조하거나 반박하되, 대상 글 제목/본문과도 연결해라.";
        }

        String postTitle = targetPost == null ? "" : safeText(targetPost.getTitle(), 60);
        return "글 제목 '" + postTitle + "' 아래 최신 댓글은 " + latestSummary + " 이고, 가장 최근 타인 댓글 흐름은 " + latestOtherSummary
                + " 이다. 둘 중 하나에 분명히 반응하면서 글 본문과도 이어라.";
    }

    private boolean isKnownBotName(String nickname) {
        if (!StringUtils.hasText(nickname)) {
            return false;
        }
        for (PersonaProperties persona : botProperties.getEnabledPersonas()) {
            if (isBotIdentity(nickname, persona)) {
                return true;
            }
        }
        return false;
    }

    private int[] countRecentPostTopicLanes(List<AssistantBotHistoryDTO> recentHistory) {
        int[] counts = new int[4];
        for (AssistantBotHistoryDTO history : recentHistory) {
            if (history == null || !MODE_POST.equals(normalizeMode(history.getGenerationMode()))) {
                continue;
            }
            String lane = classifyTopicLane(history);
            if (TOPIC_LANE_STAR_CHAT.equals(lane)) {
                counts[0]++;
            } else if (TOPIC_LANE_DAILY_LIFE.equals(lane)) {
                counts[1]++;
            } else if (TOPIC_LANE_LIGHT_CHAT.equals(lane)) {
                counts[2]++;
            } else {
                counts[3]++;
            }
        }
        return counts;
    }

    private String classifyTopicLane(AssistantBotHistoryDTO history) {
        String source = (safeText(history.getTopic(), 120) + " " + safeText(history.getDraftTitle(), 120) + " " + safeText(history.getDraftBody(), 200))
                .toLowerCase(Locale.ROOT);

        if (containsAny(source, "출근", "퇴근", "회사", "직장", "점심", "저녁", "아침", "잠", "수면", "피곤", "날씨", "주말", "월요일", "화요일", "수요일", "목요일", "금요일")) {
            return TOPIC_LANE_DAILY_LIFE;
        }
        if (containsAny(source, "프로토스", "테란", "저그", "스타", "래더", "빌드", "리버", "드라군", "질럿", "캐리어", "뮤탈", "탱크", "리플", "asl", "asl", "경기", "맵", "종족")) {
            if (containsAny(source, "억까", "사기", "너프", "상향", "밸런스", "불리", "개빡", "징징")) {
                return TOPIC_LANE_WHIRING;
            }
            return TOPIC_LANE_STAR_CHAT;
        }
        if (containsAny(source, "억까", "사기", "너프", "상향", "밸런스", "불리", "개빡", "징징")) {
            return TOPIC_LANE_WHIRING;
        }
        return TOPIC_LANE_LIGHT_CHAT;
    }

    private boolean containsAny(String text, String... keywords) {
        if (!StringUtils.hasText(text) || keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean isRaceGamePersona(PersonaProperties persona) {
        return hasPersonaName(persona, "프징징봇")
                || hasPersonaName(persona, "테뻔뻔봇")
                || hasPersonaName(persona, "저묵묵봇");
    }

    private String resolveRaceLabel(PersonaProperties persona) {
        if (hasPersonaName(persona, "테뻔뻔봇")) {
            return "테란";
        }
        if (hasPersonaName(persona, "저묵묵봇")) {
            return "저그";
        }
        return "프로토스";
    }

    private String resolveRaceKeywordHint(PersonaProperties persona) {
        if (hasPersonaName(persona, "테뻔뻔봇")) {
            return "테란, 마린, 벌처, 탱크, 배슬, 팩토리";
        }
        if (hasPersonaName(persona, "저묵묵봇")) {
            return "저그, 저글링, 히드라, 뮤탈, 럴커, 디파일러";
        }
        return "프로토스, 토스, 질럿, 드라군, 리버, 셔틀, 캐리어";
    }

    private boolean containsGameTalkKeyword(String text) {
        return containsAny(text,
                "스타", "래더", "빌드", "운영", "정찰", "멀티", "앞마당", "본진", "교전", "견제",
                "타이밍", "러시", "푸시", "올인", "한방", "조합", "업글", "컨트롤", "리플", "경기",
                "맵", "상성", "종족", "apm", "asl", "프로토스", "토스", "테란", "저그",
                "질럿", "드라군", "하템", "템플러", "아콘", "리버", "셔틀", "캐리어", "커세어",
                "마린", "메딕", "벌처", "탱크", "시즈", "골리앗", "레이스", "배슬", "바이오닉", "메카닉",
                "저글링", "히드라", "뮤탈", "럴커", "러커", "디파일러", "울트라", "드론", "오버로드");
    }

    private boolean containsOwnRaceKeyword(PersonaProperties persona, String text) {
        if (hasPersonaName(persona, "테뻔뻔봇")) {
            return containsAny(text,
                    "테란", "마린", "메딕", "벌처", "탱크", "시즈", "골리앗", "레이스", "배슬",
                    "바이오닉", "메카닉", "서플", "배럭", "팩토리", "스타포트", "커맨드", "벙커");
        }
        if (hasPersonaName(persona, "저묵묵봇")) {
            return containsAny(text,
                    "저그", "저글링", "히드라", "뮤탈", "럴커", "러커", "디파일러", "울트라",
                    "드론", "오버로드", "해처리", "레어", "하이브", "성큰", "스포어");
        }
        return containsAny(text,
                "프로토스", "토스", "질럿", "드라군", "하템", "템플러", "아콘", "리버",
                "셔틀", "캐리어", "커세어", "다크", "다템", "옵저버", "넥서스", "게이트", "파일런");
    }

    private boolean containsLifestyleOnlyKeyword(String text) {
        return containsAny(text,
                "출근", "퇴근", "회사", "직장", "점심", "저녁", "아침", "식사", "커피",
                "날씨", "비오", "비 와", "눈 와", "주말", "월요일", "화요일", "수요일", "목요일", "금요일",
                "수면", "피곤", "운동", "건강", "병원", "감기", "청소", "집안일");
    }

    private boolean isGameTalkPost(BoardDTO post) {
        if (post == null) {
            return false;
        }
        String text = (safeText(post.getTitle(), 240) + " " + safeText(stripHtml(post.getContent()), 600))
                .toLowerCase(Locale.ROOT);
        return containsGameTalkKeyword(text);
    }

    private boolean isRepetitiveByDesignPersona(PersonaProperties persona) {
        return hasPersonaName(persona, "야옹봇");
    }

    private boolean shouldUseRecentContext(PersonaProperties persona) {
        return !isRepetitiveByDesignPersona(persona);
    }

    private boolean supportsAutoComment(PersonaProperties persona) {
        return !isRepetitiveByDesignPersona(persona);
    }

    private boolean isHealthPersona(PersonaProperties persona) {
        return hasPersonaName(persona, "건강봇");
    }

    private boolean hasPersonaName(PersonaProperties persona, String expectedName) {
        return persona != null
                && StringUtils.hasText(persona.getName())
                && Objects.equals(persona.getName().trim(), expectedName);
    }

    private String buildRecentTitleOpeningHint(List<BoardDTO> recentPosts, List<AssistantBotHistoryDTO> recentHistory) {
        LinkedHashSet<String> openings = new LinkedHashSet<>();
        for (BoardDTO post : recentPosts) {
            if (post == null) {
                continue;
            }
            addTitleOpeningHint(openings, post.getTitle());
            if (openings.size() >= 6) {
                break;
            }
        }
        if (openings.size() < 6) {
            for (AssistantBotHistoryDTO history : recentHistory) {
                if (history == null || !MODE_POST.equals(normalizeMode(history.getGenerationMode()))) {
                    continue;
                }
                addTitleOpeningHint(openings, history.getDraftTitle());
                if (openings.size() >= 6) {
                    break;
                }
            }
        }
        if (openings.isEmpty()) {
            return "최근 제목 오프닝 예시가 없으면, 이번 제목은 평소보다 더 다른 시작 어휘로 출발하라.";
        }
        return "최근 재사용 금지 오프닝 예시: " + String.join(" / ", openings);
    }

    private String buildRecentTitleKeywordHint(List<BoardDTO> recentPosts, String recommendedStrategy) {
        Set<String> hotKeywords = collectFrequentRecentTitleKeywords(recentPosts, 8);
        if (hotKeywords.isEmpty()) {
            if (POST_STRATEGY_LINKED.equals(recommendedStrategy)) {
                return "최신글 제목에서 핵심 단어 하나만 앵커로 잡고 나머지 제목 표현은 전부 새로 바꿔라.";
            }
            return "최신글 제목과 겹치는 단어를 되도록 피하고 다른 장면이나 화제로 이동하라.";
        }
        if (POST_STRATEGY_LINKED.equals(recommendedStrategy)) {
            return "연계 글이면 다음 단어 중 하나만 앵커로 쓰고 나머지는 피하라: " + String.join(", ", hotKeywords);
        }
        return "신규 글이면 다음 단어들을 제목에서 되도록 피하라: " + String.join(", ", hotKeywords);
    }

    private void addTitleOpeningHint(Set<String> openings, String title) {
        String opening = extractTitleOpeningHint(title);
        if (StringUtils.hasText(opening)) {
            openings.add(opening);
        }
    }

    private String extractTitleOpeningHint(String title) {
        String normalized = normalizeTitleForOpeningCompare(title);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String[] tokens = normalized.split(" ");
        if (tokens.length >= 3) {
            return String.join(" ", tokens[0], tokens[1], tokens[2]);
        }
        if (tokens.length == 2) {
            return String.join(" ", tokens[0], tokens[1]);
        }
        if (normalized.length() <= 12) {
            return normalized;
        }
        return normalized.substring(0, 12).trim();
    }

    private String buildLeadingTokenKey(String text, int tokenCount) {
        if (!StringUtils.hasText(text) || tokenCount <= 0) {
            return "";
        }
        String[] tokens = text.trim().split("\\s+");
        if (tokens.length < tokenCount) {
            return "";
        }
        List<String> selected = new ArrayList<>();
        for (int i = 0; i < tokenCount; i++) {
            if (StringUtils.hasText(tokens[i])) {
                selected.add(tokens[i]);
            }
        }
        return String.join(" ", selected);
    }

    private int sharedPrefixLength(String left, String right) {
        int max = Math.min(left.length(), right.length());
        int matched = 0;
        while (matched < max && left.charAt(matched) == right.charAt(matched)) {
            matched++;
        }
        return matched;
    }

    private String inferPostStrategy(String title, List<BoardDTO> recentPosts) {
        if (!StringUtils.hasText(title) || recentPosts == null || recentPosts.isEmpty()) {
            return POST_STRATEGY_FRESH;
        }
        Set<String> generatedKeywords = extractTitleKeywords(title);
        Set<String> latestKeywords = extractTitleKeywords(recentPosts.get(0) == null ? null : recentPosts.get(0).getTitle());
        return countOverlap(generatedKeywords, latestKeywords) >= 1 ? POST_STRATEGY_LINKED : POST_STRATEGY_FRESH;
    }

    private Set<String> collectFrequentRecentTitleKeywords(List<BoardDTO> recentPosts, int limit) {
        if (recentPosts == null || recentPosts.isEmpty() || limit <= 0) {
            return Collections.emptySet();
        }
        Map<String, Integer> counts = new HashMap<>();
        List<String> firstSeenOrder = new ArrayList<>();
        for (BoardDTO post : recentPosts) {
            if (post == null) {
                continue;
            }
            for (String keyword : extractTitleKeywords(post.getTitle())) {
                counts.put(keyword, counts.getOrDefault(keyword, 0) + 1);
                if (counts.get(keyword) == 1) {
                    firstSeenOrder.add(keyword);
                }
            }
        }

        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (String keyword : firstSeenOrder) {
            if (selected.size() >= limit) {
                break;
            }
            if (counts.getOrDefault(keyword, 0) >= 2) {
                selected.add(keyword);
            }
        }
        if (!selected.isEmpty()) {
            return selected;
        }

        for (String keyword : firstSeenOrder) {
            if (selected.size() >= limit) {
                break;
            }
            selected.add(keyword);
        }
        return selected;
    }

    private Set<String> extractTitleKeywords(String title) {
        String normalized = normalizeTitleForOpeningCompare(title);
        if (!StringUtils.hasText(normalized)) {
            return Collections.emptySet();
        }
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        for (String token : normalized.split("\\s+")) {
            String cleaned = normalizeText(token);
            if (!StringUtils.hasText(cleaned) || cleaned.length() < 2) {
                continue;
            }
            keywords.add(cleaned);
        }
        return keywords;
    }

    private int countOverlap(Set<String> left, Set<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        int overlap = 0;
        for (String item : left) {
            if (right.contains(item)) {
                overlap++;
            }
        }
        return overlap;
    }

    private static final class CandidateDraft {
        private boolean accepted;
        private JsonNode result;
        private String title;
        private String body;
        private String topic;
        private boolean shouldReply;
        private String feedback;

        static CandidateDraft rejected(String feedback) {
            CandidateDraft draft = new CandidateDraft();
            draft.accepted = false;
            draft.feedback = feedback;
            return draft;
        }
    }

    private static final class ChatCandidate {
        private boolean accepted;
        private JsonNode result;
        private String body;
        private String topic;
        private String feedback;

        static ChatCandidate accepted(JsonNode result, String body, String topic) {
            ChatCandidate candidate = new ChatCandidate();
            candidate.accepted = true;
            candidate.result = result;
            candidate.body = body;
            candidate.topic = topic;
            return candidate;
        }

        static ChatCandidate rejected(String feedback) {
            ChatCandidate candidate = new ChatCandidate();
            candidate.accepted = false;
            candidate.feedback = feedback;
            return candidate;
        }
    }

    private static final class DuplicateCheck {
        private final boolean duplicate;
        private final String feedback;

        private DuplicateCheck(boolean duplicate, String feedback) {
            this.duplicate = duplicate;
            this.feedback = feedback;
        }

        static DuplicateCheck of(String feedback) {
            return new DuplicateCheck(true, feedback);
        }

        static DuplicateCheck notDuplicate() {
            return new DuplicateCheck(false, null);
        }
    }

    private static final class KeywordOverlapCheck {
        private final boolean invalid;
        private final String feedback;

        private KeywordOverlapCheck(boolean invalid, String feedback) {
            this.invalid = invalid;
            this.feedback = feedback;
        }

        static KeywordOverlapCheck of(String feedback) {
            return new KeywordOverlapCheck(true, feedback);
        }

        static KeywordOverlapCheck valid() {
            return new KeywordOverlapCheck(false, null);
        }
    }

    private static final class AutoPublishCandidate {
        private final String mode;
        private final int minuteOfDay;

        private AutoPublishCandidate(String mode, int minuteOfDay) {
            this.mode = mode;
            this.minuteOfDay = minuteOfDay;
        }
    }

    private static final class GenerateCallBudgetResult {
        private final boolean allowed;
        private final String message;

        private GenerateCallBudgetResult(boolean allowed, String message) {
            this.allowed = allowed;
            this.message = message;
        }

        private static GenerateCallBudgetResult allowed() {
            return new GenerateCallBudgetResult(true, null);
        }

        private static GenerateCallBudgetResult blocked(String message) {
            return new GenerateCallBudgetResult(false, message);
        }

        private boolean isAllowed() {
            return allowed;
        }

        private String getMessage() {
            return message;
        }
    }

    private static final class ThreadCommentState {
        private final boolean prioritizeSelfPostReply;
        private final boolean canAddCommentToOthersPost;

        private ThreadCommentState(boolean prioritizeSelfPostReply, boolean canAddCommentToOthersPost) {
            this.prioritizeSelfPostReply = prioritizeSelfPostReply;
            this.canAddCommentToOthersPost = canAddCommentToOthersPost;
        }

        static ThreadCommentState empty() {
            return new ThreadCommentState(false, true);
        }

        boolean shouldPrioritizeSelfPostReply() {
            return prioritizeSelfPostReply;
        }

        boolean canAddCommentToOthersPost() {
            return canAddCommentToOthersPost;
        }
    }

    public static final class AutoPublishResult {
        public static final String OUTCOME_SKIPPED = STATUS_SKIPPED;
        public static final String OUTCOME_FAILED = AssistantBotService.OUTCOME_FAILED;
        public static final String OUTCOME_PUBLISHED = STATUS_PUBLISHED;

        private final String outcome;
        private final String personaName;
        private final String detail;
        private final String mode;
        private final Long historyId;
        private final Integer publishedPostNum;
        private final String redirectUrl;

        private AutoPublishResult(String outcome, String personaName, String detail, String mode, Long historyId, Integer publishedPostNum, String redirectUrl) {
            this.outcome = outcome;
            this.personaName = personaName;
            this.detail = detail;
            this.mode = mode;
            this.historyId = historyId;
            this.publishedPostNum = publishedPostNum;
            this.redirectUrl = redirectUrl;
        }

        public static AutoPublishResult skipped(String detail) {
            return skipped(null, detail);
        }

        public static AutoPublishResult skipped(String personaName, String detail) {
            return new AutoPublishResult(OUTCOME_SKIPPED, personaName, detail, null, null, null, null);
        }

        public static AutoPublishResult failed(String detail) {
            return failed(null, detail);
        }

        public static AutoPublishResult failed(String personaName, String detail) {
            return new AutoPublishResult(OUTCOME_FAILED, personaName, detail, null, null, null, null);
        }

        public static AutoPublishResult published(String personaName, String mode, Long historyId, Integer publishedPostNum, String redirectUrl) {
            return new AutoPublishResult(OUTCOME_PUBLISHED, personaName, null, mode, historyId, publishedPostNum, redirectUrl);
        }

        public String getOutcome() {
            return outcome;
        }

        public String getPersonaName() {
            return personaName;
        }

        public String getDetail() {
            return detail;
        }

        public String getMode() {
            return mode;
        }

        public Long getHistoryId() {
            return historyId;
        }

        public Integer getPublishedPostNum() {
            return publishedPostNum;
        }

        public String getRedirectUrl() {
            return redirectUrl;
        }
    }
}
