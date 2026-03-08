package com.sc1hub.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.assistant.config.AssistantBotProperties;
import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.assistant.dto.AssistantBotDraftRequestDTO;
import com.sc1hub.assistant.dto.AssistantBotDraftResponseDTO;
import com.sc1hub.assistant.dto.AssistantBotHistoryDTO;
import com.sc1hub.assistant.dto.AssistantBotPublishResponseDTO;
import com.sc1hub.assistant.gemini.GeminiClient;
import com.sc1hub.assistant.gemini.GeminiException;
import com.sc1hub.assistant.mapper.AssistantBotMapper;
import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.board.dto.CommentDTO;
import com.sc1hub.board.mapper.BoardMapper;
import com.sc1hub.board.service.BoardService;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AssistantBotService {

    private static final String MODE_POST = "post";
    private static final String MODE_COMMENT = "comment";
    private static final Pattern NON_TEXT_PATTERN = Pattern.compile("[^가-힣a-z0-9]");

    private final AssistantBotProperties botProperties;
    private final AssistantProperties assistantProperties;
    private final BoardService boardService;
    private final BoardMapper boardMapper;
    private final AssistantBotMapper assistantBotMapper;
    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public AssistantBotService(AssistantBotProperties botProperties,
                               AssistantProperties assistantProperties,
                               BoardService boardService,
                               BoardMapper boardMapper,
                               AssistantBotMapper assistantBotMapper,
                               GeminiClient geminiClient,
                               ObjectMapper objectMapper) {
        this(botProperties, assistantProperties, boardService, boardMapper, assistantBotMapper, geminiClient, objectMapper,
                Clock.systemDefaultZone());
    }

    AssistantBotService(AssistantBotProperties botProperties,
                               AssistantProperties assistantProperties,
                               BoardService boardService,
                               BoardMapper boardMapper,
                               AssistantBotMapper assistantBotMapper,
                               GeminiClient geminiClient,
                               ObjectMapper objectMapper,
                               Clock clock) {
        this.botProperties = botProperties;
        this.assistantProperties = assistantProperties;
        this.boardService = boardService;
        this.boardMapper = boardMapper;
        this.assistantBotMapper = assistantBotMapper;
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public AssistantBotDraftResponseDTO generateDraft(AssistantBotDraftRequestDTO request) {
        AssistantBotDraftResponseDTO response = new AssistantBotDraftResponseDTO();
        response.setPersonaName(botProperties.getPersonaName());

        if (!botProperties.isEnabled()) {
            response.setError("봇 초안 생성 기능이 비활성화되어 있습니다.");
            return response;
        }

        String mode = normalizeMode(request == null ? null : request.getMode());
        if (!MODE_POST.equals(mode) && !MODE_COMMENT.equals(mode)) {
            response.setError("mode는 post 또는 comment 여야 합니다.");
            return response;
        }

        String boardTitle = resolveBoardTitle(request == null ? null : request.getBoardTitle());
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
            int recentPostLimit = resolveLimit(request == null ? null : request.getRecentPostLimit(), botProperties.getRecentPostLimit());
            int recentCommentLimit = resolveLimit(request == null ? null : request.getRecentCommentLimit(), botProperties.getRecentCommentLimit());
            int recentHistoryLimit = Math.max(1, botProperties.getRecentHistoryLimit());
            int maxAttempts = Math.max(1, botProperties.getMaxGenerateAttempts());

            List<BoardDTO> recentPosts = safeList(boardMapper.selectRecentPostsForBot(boardTitle, recentPostLimit));
            List<CommentDTO> recentComments = safeList(boardMapper.selectRecentCommentsForBot(boardTitle, targetPostNum, recentCommentLimit));
            List<AssistantBotHistoryDTO> recentHistory = safeList(
                    assistantBotMapper.selectRecentHistory(botProperties.getPersonaName(), boardTitle, recentHistoryLimit)
            );

            BoardDTO targetPost = null;
            if (MODE_COMMENT.equals(mode)) {
                targetPost = boardMapper.readPost(boardTitle, targetPostNum);
                if (targetPost == null) {
                    response.setError("대상 게시글을 찾지 못했습니다.");
                    return response;
                }
            }

            CandidateDraft acceptedDraft = null;
            String retryFeedback = null;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                String prompt = buildPrompt(mode, boardTitle, targetPost, recentPosts, recentComments, recentHistory, retryFeedback, attempt, maxAttempts);
                String rawJson = geminiClient.generateAnswer(
                        prompt,
                        Math.max(1, botProperties.getMaxOutputTokens()),
                        botProperties.getModel()
                );
                JsonNode result = parseJson(rawJson);
                CandidateDraft candidate = validateCandidate(mode, result, recentPosts, recentComments, recentHistory);
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
            history.setPersonaName(botProperties.getPersonaName());
            history.setBoardTitle(boardTitle);
            history.setGenerationMode(mode);
            history.setTargetPostNum(targetPostNum);
            history.setTopic(acceptedDraft.topic);
            history.setDraftTitle(acceptedDraft.title);
            history.setDraftBody(acceptedDraft.body == null ? "" : acceptedDraft.body);
            history.setRawJson(acceptedDraft.result.toString());
            history.setStatus(MODE_COMMENT.equals(mode) && !acceptedDraft.shouldReply ? "skipped" : "draft");
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

            response.setBoardTitle(history.getBoardTitle());
            response.setMode(history.getGenerationMode());
            response.setStatus(history.getStatus());
            response.setTargetPostNum(history.getTargetPostNum());
            response.setPublishedPostNum(history.getPublishedPostNum());

            if (resolveBoardTitle(history.getBoardTitle()) == null) {
                response.setError("현재는 설정된 대상 게시판의 초안만 발행할 수 있습니다.");
                return response;
            }
            if ("published".equals(history.getStatus())) {
                response.setError("이미 발행된 초안입니다.");
                return response;
            }
            if (!"draft".equals(history.getStatus()) && !"skipped".equals(history.getStatus())) {
                response.setError("발행 가능한 초안 상태가 아닙니다.");
                return response;
            }

            JsonNode result = parseJson(history.getRawJson());
            if (result == null || result.isMissingNode() || result.isNull()) {
                response.setError("저장된 초안 JSON을 읽지 못했습니다.");
                return response;
            }

            if (MODE_POST.equals(normalizeMode(history.getGenerationMode()))) {
                BoardDTO post = new BoardDTO();
                post.setWriter(botProperties.getPersonaName());
                post.setGuestPassword(botProperties.getPublishGuestPassword());
                post.setTitle(safeTitleForPublish(textOrNull(result.path("post").path("title"))));
                post.setContent(toHtmlBody(textOrNull(result.path("post").path("body"))));
                post.setNotice(0);
                if (!StringUtils.hasText(post.getTitle()) || !StringUtils.hasText(post.getContent())) {
                    response.setError("발행할 게시글 제목 또는 본문이 비어 있습니다.");
                    return response;
                }
                boardService.submitPost(history.getBoardTitle(), post);
                BoardDTO created = findPublishedPost(history.getBoardTitle(), post.getTitle(), botProperties.getPersonaName());
                Integer publishedPostNum = created == null ? null : created.getPostNum();
                assistantBotMapper.updateStatus(historyId, "published", publishedPostNum);
                response.setStatus("published");
                response.setPublishedPostNum(publishedPostNum);
                response.setRedirectUrl(publishedPostNum == null
                        ? "/boards/" + history.getBoardTitle()
                        : "/boards/" + history.getBoardTitle() + "/readPost?postNum=" + publishedPostNum);
                return response;
            }

            boolean shouldReply = resolveShouldReply(history.getGenerationMode(), result);
            if (!shouldReply) {
                assistantBotMapper.updateStatus(historyId, "published", history.getTargetPostNum());
                response.setStatus("published");
                response.setPublishedPostNum(history.getTargetPostNum());
                response.setRedirectUrl("/boards/" + history.getBoardTitle() + "/readPost?postNum=" + history.getTargetPostNum());
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
            comment.setNickname(botProperties.getPersonaName());
            comment.setPassword(botProperties.getPublishGuestPassword());
            comment.setContent(safeCommentForPublish(textOrNull(result.path("reply").path("body"))));
            if (!StringUtils.hasText(comment.getContent())) {
                response.setError("발행할 댓글 본문이 비어 있습니다.");
                return response;
            }
            boardService.addComment(history.getBoardTitle(), comment);
            boardService.updateCommentCount(history.getBoardTitle(), history.getTargetPostNum());
            assistantBotMapper.updateStatus(historyId, "published", history.getTargetPostNum());
            response.setStatus("published");
            response.setPublishedPostNum(history.getTargetPostNum());
            response.setRedirectUrl("/boards/" + history.getBoardTitle() + "/readPost?postNum=" + history.getTargetPostNum());
            return response;
        } catch (Exception e) {
            log.error("봇 초안 발행 실패. historyId={}", historyId, e);
            response.setError("봇 초안 발행 중 오류가 발생했습니다.");
            return response;
        }
    }

    public AutoPublishResult autoPublishOnce() {
        if (!botProperties.isEnabled() || !botProperties.isAutoPublishEnabled()) {
            return AutoPublishResult.skipped("autoPublishEnabled=false");
        }

        String boardTitle = resolveBoardTitle(botProperties.getBoardTitle());
        if (!StringUtils.hasText(boardTitle)) {
            return AutoPublishResult.skipped("invalid_board");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        LocalDateTime since = today.atStartOfDay();

        int publishedPostToday = assistantBotMapper.countPublishedSinceByMode(boardTitle, MODE_POST, since);
        int publishedCommentToday = assistantBotMapper.countPublishedSinceByMode(boardTitle, MODE_COMMENT, since);

        List<AutoPublishCandidate> dueCandidates = resolveDueAutoPublishCandidates(today, now.toLocalTime(),
                publishedPostToday, publishedCommentToday);
        if (dueCandidates.isEmpty()) {
            return AutoPublishResult.skipped(buildWaitingDetail(today, now.toLocalTime(), publishedPostToday, publishedCommentToday));
        }

        String lastSkippedDetail = null;
        for (AutoPublishCandidate candidate : dueCandidates) {
            AssistantBotDraftRequestDTO request = buildAutoDraftRequest(boardTitle, candidate.mode);
            if (request == null) {
                lastSkippedDetail = "no_target_for_mode:" + candidate.mode;
                continue;
            }

            AssistantBotDraftResponseDTO draft = generateDraft(request);
            if (StringUtils.hasText(draft.getError())) {
                return AutoPublishResult.failed("draft_error:" + draft.getError());
            }
            if (draft.getHistoryId() == null) {
                return AutoPublishResult.failed("missing_history_id");
            }
            if ("skipped".equals(draft.getStatus())) {
                lastSkippedDetail = "draft_skipped:" + candidate.mode;
                continue;
            }

            AssistantBotPublishResponseDTO published = publishDraft(draft.getHistoryId());
            if (StringUtils.hasText(published.getError())) {
                return AutoPublishResult.failed("publish_error:" + published.getError());
            }
            return AutoPublishResult.published(request.getMode(), draft.getHistoryId(), published.getPublishedPostNum(), published.getRedirectUrl());
        }

        return AutoPublishResult.skipped(lastSkippedDetail == null ? "no_due_candidate" : lastSkippedDetail);
    }

    private List<AutoPublishCandidate> resolveDueAutoPublishCandidates(LocalDate date,
                                                                       LocalTime now,
                                                                       int publishedPostToday,
                                                                       int publishedCommentToday) {
        List<AutoPublishCandidate> candidates = new ArrayList<>();

        Integer postSlot = resolveNextRandomAutoPublishSlot(date, MODE_POST,
                botProperties.getAutoPublishPostDailyLimit(), publishedPostToday);
        if (postSlot != null && toMinuteOfDay(now) >= postSlot) {
            candidates.add(new AutoPublishCandidate(MODE_POST, postSlot));
        }

        Integer commentSlot = resolveNextRandomAutoPublishSlot(date, MODE_COMMENT,
                botProperties.getAutoPublishCommentDailyLimit(), publishedCommentToday);
        if (commentSlot != null && toMinuteOfDay(now) >= commentSlot) {
            candidates.add(new AutoPublishCandidate(MODE_COMMENT, commentSlot));
        }

        candidates.sort(Comparator.comparingInt(candidate -> candidate.minuteOfDay));
        return candidates;
    }

    private String buildWaitingDetail(LocalDate date,
                                      LocalTime now,
                                      int publishedPostToday,
                                      int publishedCommentToday) {
        Integer nextPostSlot = resolveNextRandomAutoPublishSlot(date, MODE_POST,
                botProperties.getAutoPublishPostDailyLimit(), publishedPostToday);
        Integer nextCommentSlot = resolveNextRandomAutoPublishSlot(date, MODE_COMMENT,
                botProperties.getAutoPublishCommentDailyLimit(), publishedCommentToday);
        if (nextPostSlot == null && nextCommentSlot == null) {
            return "daily_limits_reached";
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

    private Integer resolveNextRandomAutoPublishSlot(LocalDate date, String mode, int dailyLimit, int publishedCount) {
        List<Integer> slots = botProperties.buildDailyAutoPublishSlots(date, mode, dailyLimit);
        if (publishedCount < 0 || publishedCount >= slots.size()) {
            return null;
        }
        return slots.get(publishedCount);
    }

    private int toMinuteOfDay(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    private String formatMinuteOfDay(int minuteOfDay) {
        return String.format("%02d:%02d", minuteOfDay / 60, minuteOfDay % 60);
    }

    private CandidateDraft validateCandidate(String mode,
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

        DuplicateCheck duplicateCheck = findDuplicateIssue(mode, title, body, recentPosts, recentComments, recentHistory);
        if (duplicateCheck.duplicate) {
            return CandidateDraft.rejected(duplicateCheck.feedback);
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

    private String buildPrompt(String mode,
                               String boardTitle,
                               BoardDTO targetPost,
                               List<BoardDTO> recentPosts,
                               List<CommentDTO> recentComments,
                               List<AssistantBotHistoryDTO> recentHistory,
                               String retryFeedback,
                               int attempt,
                               int maxAttempts) {
        StringBuilder sb = new StringBuilder();
        sb.append("너는 SC1Hub 꿀잼놀이터 게시판 전용 AI 유저 '")
                .append(botProperties.getPersonaName())
                .append("'의 글 작성 에이전트다.\n");
        sb.append("중요: 실제 사람으로 위장하지 말고, 게시판의 말투와 리듬만 자연스럽게 반영하라.\n");
        sb.append("닉네임은 이미 봇 계정이므로 특정 실존 유저를 흉내 내지 말라.\n");
        sb.append("현재 시도 횟수: ").append(attempt).append('/').append(maxAttempts).append("\n\n");

        sb.append("목표:\n");
        sb.append("- 실제 커뮤니티 상주 유저처럼 자연스럽고 재밌는 글을 쓴다.\n");
        sb.append("- 프로토스가 항상 억까당한다고 믿는 징징 결을 기본으로 하되, 뻘글/추억글/과몰입글도 섞는다.\n");
        sb.append("- 사이트 밈, 최근 화제, 자주 보이는 표현을 반영한다.\n\n");

        sb.append("금지:\n");
        sb.append("- 정치, 혐오, 성적 표현, 실존 유저 공격, 패드립, 과한 분쟁 유도\n");
        sb.append("- 너무 반듯한 AI 문체\n");
        sb.append("- 반복되는 제목 패턴\n");
        sb.append("- 최근 글과 유사한 주제/표현 재사용\n");
        sb.append("- 금칙어 사용\n\n");

        sb.append("중복 회피 규칙:\n");
        sb.append("- 최근 프징징봇 초안의 제목, 화제, 본문 전개를 재사용하지 않는다.\n");
        sb.append("- 최근 게시글 제목과 너무 비슷한 제목을 만들지 않는다.\n");
        sb.append("- 최근 댓글과 같은 결론, 어미, 첫 문장을 반복하지 않는다.\n\n");

        sb.append("인간스러움 규칙:\n");
        sb.append("- 완벽하게 정제된 문장보다 커뮤니티스러운 리듬을 우선한다.\n");
        sb.append("- 설명 과잉 금지.\n");
        sb.append("- 구체적 장면이나 감정 한 조각을 넣는다.\n");
        sb.append("- 매번 같은 어미, 같은 농담, 같은 길이를 쓰지 않는다.\n");
        sb.append("- 진심 60 / 드립 40 정도의 결을 유지한다.\n\n");
        sb.append("형식 규칙:\n");
        sb.append("- 게시글/댓글 본문이 두 문장 이상이면 한 문장마다 줄바꿈한다.\n");
        sb.append("- 문단을 길게 붙여 쓰지 말고, 문장 하나를 한 줄로 둔다.\n\n");

        if (StringUtils.hasText(retryFeedback)) {
            sb.append("직전 시도 보정 지시:\n");
            appendLine(sb, "- " + retryFeedback);
            sb.append("- 이번 시도에서는 제목, 첫 문장, 핵심 감정선을 분명히 바꾼다.\n\n");
        }

        sb.append("입력:\n");
        sb.append("1. 최근 게시글/댓글 데이터\n");
        sb.append("2. 최근 작성한 프징징봇 글 목록\n");
        sb.append("3. 오늘의 생성 모드(").append(MODE_COMMENT.equals(mode) ? "댓글" : "게시글").append(")\n");
        sb.append("4. 게시판 이름(").append(boardTitle).append(")\n\n");

        if (targetPost != null) {
            sb.append("대상 게시글:\n");
            appendLine(sb, "- 제목: " + safeText(targetPost.getTitle(), 160));
            appendLine(sb, "- 본문: " + safeText(stripHtml(targetPost.getContent()), botProperties.getPromptExcerptChars()));
            sb.append("\n");
        }

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

        sb.append("\n최근 프징징봇 초안:\n");
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

        sb.append("\n작업 절차:\n");
        sb.append("1. 최근 게시판의 밈, 말투, 화제, 금지해야 할 패턴을 분석한다.\n");
        sb.append("2. 현재 타이밍에 맞는 글감 후보를 여러 개 만든다.\n");
        sb.append("3. 최근 프징징봇 글과 겹치지 않는 후보를 고른다.\n");
        sb.append("4. 자연스러운 제목/본문 또는 댓글을 작성한다.\n");
        sb.append("5. 자기검수 후, 어색하면 스스로 수정한다.\n\n");

        sb.append("출력은 반드시 JSON 하나만 반환한다.\n");
        if (MODE_POST.equals(mode)) {
            sb.append("{\"analysis\":{\"memes\":[],\"topic\":\"\",\"risk_notes\":[]},");
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

    private AssistantBotDraftRequestDTO buildAutoDraftRequest(String boardTitle, String mode) {
        AssistantBotDraftRequestDTO request = new AssistantBotDraftRequestDTO();
        request.setBoardTitle(boardTitle);
        request.setMode(mode);
        request.setRecentPostLimit(botProperties.getRecentPostLimit());
        request.setRecentCommentLimit(botProperties.getRecentCommentLimit());

        if (MODE_COMMENT.equals(mode)) {
            Integer targetPostNum = pickAutoCommentTarget(boardTitle);
            if (targetPostNum == null || targetPostNum <= 0) {
                return null;
            }
            request.setTargetPostNum(targetPostNum);
        }
        return request;
    }

    private Integer pickAutoCommentTarget(String boardTitle) {
        int candidateLimit = Math.max(1, Math.min(botProperties.getAutoPublishCommentCandidatePosts(), 20));
        try {
            List<BoardDTO> posts = safeList(boardMapper.selectRecentPostsForBot(boardTitle, candidateLimit));
            for (BoardDTO post : posts) {
                if (post == null || post.getPostNum() <= 0) {
                    continue;
                }
                if (Objects.equals(botProperties.getPersonaName(), post.getWriter())) {
                    continue;
                }
                return post.getPostNum();
            }
        } catch (Exception e) {
            log.warn("자동 댓글 대상 게시글 조회 실패. boardTitle={}", boardTitle, e);
        }
        return null;
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
                if (isTooSimilar(normalizedTitle, normalizeText(history.getDraftTitle()), threshold)) {
                    return DuplicateCheck.of("최근 프징징봇 게시글 제목과 너무 비슷합니다.");
                }
                if (isTooSimilar(normalizedBody, normalizeText(history.getDraftBody()), threshold)) {
                    return DuplicateCheck.of("최근 프징징봇 게시글 본문과 너무 비슷합니다.");
                }
            }
            if (MODE_COMMENT.equals(mode) && MODE_COMMENT.equals(historyMode)) {
                if (isTooSimilar(normalizedBody, normalizeText(history.getDraftBody()), threshold)) {
                    return DuplicateCheck.of("최근 프징징봇 댓글과 너무 비슷합니다.");
                }
            }
        }

        if (MODE_POST.equals(mode)) {
            for (BoardDTO post : recentPosts) {
                if (post == null) {
                    continue;
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

    private boolean resolveShouldReply(String mode, JsonNode result) {
        if (!MODE_COMMENT.equals(normalizeMode(mode))) {
            return true;
        }
        JsonNode shouldReply = result.path("reply").path("should_reply");
        return shouldReply.isMissingNode() || shouldReply.isNull() || shouldReply.asBoolean(true);
    }

    private String findBlockedWord(String... texts) {
        List<String> blockedWords = assistantProperties.getBlockedWords();
        if (blockedWords == null || blockedWords.isEmpty()) {
            return null;
        }

        for (String text : texts) {
            if (!StringUtils.hasText(text)) {
                continue;
            }

            String normalized = normalizeText(text);
            for (String blocked : blockedWords) {
                String token = normalizeText(blocked);
                if (!StringUtils.hasText(token)) {
                    continue;
                }
                // Single-syllable substring matching overblocks natural Korean sentences.
                if (token.length() <= 1) {
                    continue;
                }
                if (normalized.contains(token)) {
                    return blocked;
                }
            }
        }
        return null;
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

    private String resolveBoardTitle(String boardTitle) {
        String normalized = normalizeBoardTitle(StringUtils.hasText(boardTitle) ? boardTitle : botProperties.getBoardTitle());
        String configured = normalizeBoardTitle(botProperties.getBoardTitle());
        if (!StringUtils.hasText(normalized) || !Objects.equals(normalized, configured)) {
            return null;
        }
        return normalized;
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

    private String safeTitleForPublish(String title) {
        if (!StringUtils.hasText(title)) {
            return null;
        }
        return HtmlUtils.htmlEscape(title.trim());
    }

    private String toHtmlBody(String body) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        String escaped = HtmlUtils.htmlEscape(formatBodyForPublish(body));
        return escaped.replace("\r\n", "\n").replace('\r', '\n').replace("\n", "<br>");
    }

    private String safeCommentForPublish(String body) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        return formatBodyForPublish(body);
    }

    private String formatBodyForPublish(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        String normalized = body.trim()
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t]+", " ");
        String sentenceBroken = normalized
                .replaceAll("([.!?。！？]+)\\s+", "$1\n")
                .replaceAll("\\n{3,}", "\n\n");

        String[] lines = sentenceBroken.split("\\n");
        StringBuilder formatted = new StringBuilder();
        for (String line : lines) {
            if (!StringUtils.hasText(line)) {
                if (formatted.length() > 0 && formatted.charAt(formatted.length() - 1) != '\n') {
                    formatted.append('\n');
                }
                continue;
            }
            if (formatted.length() > 0 && formatted.charAt(formatted.length() - 1) != '\n') {
                formatted.append('\n');
            }
            formatted.append(line.trim());
        }
        return formatted.toString().trim();
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

    private static double clampThreshold(double threshold) {
        return Math.max(0.45, Math.min(threshold, 0.95));
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
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

    private static final class AutoPublishCandidate {
        private final String mode;
        private final int minuteOfDay;

        private AutoPublishCandidate(String mode, int minuteOfDay) {
            this.mode = mode;
            this.minuteOfDay = minuteOfDay;
        }
    }

    public static final class AutoPublishResult {
        private final String outcome;
        private final String detail;
        private final String mode;
        private final Long historyId;
        private final Integer publishedPostNum;
        private final String redirectUrl;

        private AutoPublishResult(String outcome, String detail, String mode, Long historyId, Integer publishedPostNum, String redirectUrl) {
            this.outcome = outcome;
            this.detail = detail;
            this.mode = mode;
            this.historyId = historyId;
            this.publishedPostNum = publishedPostNum;
            this.redirectUrl = redirectUrl;
        }

        public static AutoPublishResult skipped(String detail) {
            return new AutoPublishResult("skipped", detail, null, null, null, null);
        }

        public static AutoPublishResult failed(String detail) {
            return new AutoPublishResult("failed", detail, null, null, null, null);
        }

        public static AutoPublishResult published(String mode, Long historyId, Integer publishedPostNum, String redirectUrl) {
            return new AutoPublishResult("published", null, mode, historyId, publishedPostNum, redirectUrl);
        }

        public String getOutcome() {
            return outcome;
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
