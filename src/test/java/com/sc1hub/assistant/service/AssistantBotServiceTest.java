package com.sc1hub.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.assistant.config.AssistantBotProperties;
import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.assistant.dto.AssistantBotAutoPublishStatusDTO;
import com.sc1hub.assistant.dto.AssistantBotDraftRequestDTO;
import com.sc1hub.assistant.dto.AssistantBotDraftResponseDTO;
import com.sc1hub.assistant.dto.AssistantBotHistoryDTO;
import com.sc1hub.assistant.gemini.GeminiClient;
import com.sc1hub.assistant.mapper.AssistantBotMapper;
import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.board.dto.CommentDTO;
import com.sc1hub.board.mapper.BoardMapper;
import com.sc1hub.board.service.BoardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AssistantBotServiceTest {

    @Mock
    private BoardService boardService;

    @Mock
    private BoardMapper boardMapper;

    @Mock
    private AssistantBotMapper assistantBotMapper;

    @Mock
    private GeminiClient geminiClient;

    private AssistantBotProperties botProperties;
    private AssistantBotService assistantBotService;

    @BeforeEach
    void setUp() {
        botProperties = new AssistantBotProperties();
        botProperties.setEnabled(true);
        botProperties.setBoardTitle("funboard");
        botProperties.setPersonaName("프징징봇");
        botProperties.setAutoPublishEnabled(true);
        botProperties.setAutoPublishCommentCandidatePosts(10);
        botProperties.setRecentCommentLimit(24);
        botProperties.setAutoPublishCommentReplyPriorityProbability(0.9);
        botProperties.setPersonas(Arrays.asList(persona("프징징봇"), persona("테뻔뻔봇"), persona("저묵묵봇"), persona("훈훈봇")));

        AssistantProperties assistantProperties = new AssistantProperties();
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-09T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        assistantBotService = new AssistantBotService(
                botProperties,
                assistantProperties,
                boardService,
                boardMapper,
                assistantBotMapper,
                geminiClient,
                new ObjectMapper(),
                fixedClock
        );
    }

    @Test
    void pickAutoCommentTarget_spreadsAcrossRecentPosts_andSkipsBotOnlyThread() throws Exception {
        BoardDTO botPost = post(101, "프징징봇", 1);
        BoardDTO recentPostA = post(102, "테스터A", 0);
        BoardDTO recentPostB = post(103, "테스터B", 0);

        when(boardMapper.selectRecentPostsForBot("funboard", 10))
                .thenReturn(Arrays.asList(botPost, recentPostA, recentPostB));
        when(boardMapper.selectRecentCommentsForBot("funboard", 101, 24))
                .thenReturn(Collections.singletonList(comment("프징징봇")));

        Integer targetPostNum = assistantBotService.pickAutoCommentTarget(persona("프징징봇"), "funboard", new FixedRandom(0.95, 1));

        assertEquals(Integer.valueOf(103), targetPostNum);
        verify(boardMapper).selectRecentPostsForBot("funboard", 10);
        verify(boardMapper).selectRecentCommentsForBot("funboard", 101, 24);
    }

    @Test
    void pickAutoCommentTarget_prioritizesBotPost_whenOthersReplyToIt() throws Exception {
        BoardDTO botPost = post(201, "프징징봇", 2);
        BoardDTO recentPostA = post(202, "테스터A", 0);
        BoardDTO recentPostB = post(203, "테스터B", 0);

        when(boardMapper.selectRecentPostsForBot("funboard", 10))
                .thenReturn(Arrays.asList(botPost, recentPostA, recentPostB));
        when(boardMapper.selectRecentCommentsForBot("funboard", 201, 24))
                .thenReturn(Arrays.asList(comment("프징징봇"), comment("일반유저")));

        Integer targetPostNum = assistantBotService.pickAutoCommentTarget(persona("프징징봇"), "funboard", new FixedRandom(0.05, 0));

        assertEquals(Integer.valueOf(201), targetPostNum);
    }

    @Test
    void pickAutoCommentTarget_skipsPost_whenPersonaAlreadyCommentedAndNoNewReply() throws Exception {
        BoardDTO staleThread = post(301, "일반유저", 2);
        BoardDTO freshThread = post(302, "일반유저2", 0);

        when(boardMapper.selectRecentPostsForBot("funboard", 10))
                .thenReturn(Arrays.asList(staleThread, freshThread));
        when(boardMapper.selectRecentCommentsForBot("funboard", 301, 24))
                .thenReturn(Arrays.asList(comment("프징징봇", 2), comment("일반유저", 1)));

        Integer targetPostNum = assistantBotService.pickAutoCommentTarget(persona("프징징봇"), "funboard", new FixedRandom(0.8, 0));

        assertEquals(Integer.valueOf(302), targetPostNum);
    }

    @Test
    void pickAutoCommentTarget_allowsReturn_whenNewReplyArrivedAfterPersonaComment() throws Exception {
        BoardDTO activeThread = post(401, "일반유저", 3);

        when(boardMapper.selectRecentPostsForBot("funboard", 10))
                .thenReturn(Collections.singletonList(activeThread));
        when(boardMapper.selectRecentCommentsForBot("funboard", 401, 24))
                .thenReturn(Arrays.asList(comment("일반유저", 3), comment("프징징봇", 2), comment("일반유저", 1)));

        Integer targetPostNum = assistantBotService.pickAutoCommentTarget(persona("프징징봇"), "funboard", new FixedRandom(0.8, 0));

        assertEquals(Integer.valueOf(401), targetPostNum);
    }

    @Test
    void resolvePersona_returnsConfiguredPersonaByName() {
        AssistantBotProperties.PersonaProperties persona = botProperties.resolvePersona("테뻔뻔봇");

        assertEquals("테뻔뻔봇", persona.getName());
        assertEquals("funboard", persona.getBoardTitle());
    }

    @Test
    void autoPublishOnce_checksEveryEnabledPersonaBeforeReturning() {
        Map<String, AssistantBotService.AutoPublishResult> resultsByPersona = new HashMap<>();
        resultsByPersona.put("프징징봇", AssistantBotService.AutoPublishResult.published("프징징봇", "post", 1L, 101, "/boards/funboard/readPost?postNum=101"));
        resultsByPersona.put("테뻔뻔봇", AssistantBotService.AutoPublishResult.skipped("테뻔뻔봇", "no_due_candidate"));
        resultsByPersona.put("저묵묵봇", AssistantBotService.AutoPublishResult.failed("저묵묵봇", "draft_error:test"));
        resultsByPersona.put("훈훈봇", AssistantBotService.AutoPublishResult.published("훈훈봇", "comment", 2L, 202, "/boards/funboard/readPost?postNum=202"));

        RecordingAssistantBotService recordingService = new RecordingAssistantBotService(
                botProperties,
                new AssistantProperties(),
                boardService,
                boardMapper,
                assistantBotMapper,
                geminiClient,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-03-09T00:00:00Z"), ZoneId.of("Asia/Seoul")),
                resultsByPersona
        );

        AssistantBotService.AutoPublishResult result = recordingService.autoPublishOnce();

        assertEquals("published", result.getOutcome());
        assertEquals("훈훈봇", result.getPersonaName());
        assertEquals(Arrays.asList("프징징봇", "테뻔뻔봇", "저묵묵봇", "훈훈봇"), recordingService.getVisitedPersonas());
    }

    @Test
    void autoPublishAllPersonas_returnsEveryPersonaResult() {
        Map<String, AssistantBotService.AutoPublishResult> resultsByPersona = new HashMap<>();
        resultsByPersona.put("프징징봇", AssistantBotService.AutoPublishResult.published("프징징봇", "post", 1L, 101, "/boards/funboard/readPost?postNum=101"));
        resultsByPersona.put("테뻔뻔봇", AssistantBotService.AutoPublishResult.skipped("테뻔뻔봇", "no_due_candidate"));
        resultsByPersona.put("저묵묵봇", AssistantBotService.AutoPublishResult.failed("저묵묵봇", "draft_error:test"));
        resultsByPersona.put("훈훈봇", AssistantBotService.AutoPublishResult.published("훈훈봇", "comment", 2L, 202, "/boards/funboard/readPost?postNum=202"));

        RecordingAssistantBotService recordingService = new RecordingAssistantBotService(
                botProperties,
                new AssistantProperties(),
                boardService,
                boardMapper,
                assistantBotMapper,
                geminiClient,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-03-09T00:00:00Z"), ZoneId.of("Asia/Seoul")),
                resultsByPersona
        );

        List<AssistantBotService.AutoPublishResult> results = recordingService.autoPublishAllPersonas();

        assertEquals(4, results.size());
        assertEquals(Arrays.asList("프징징봇", "테뻔뻔봇", "저묵묵봇", "훈훈봇"), recordingService.getVisitedPersonas());
        assertEquals("프징징봇", results.get(0).getPersonaName());
        assertEquals("테뻔뻔봇", results.get(1).getPersonaName());
        assertEquals("저묵묵봇", results.get(2).getPersonaName());
        assertEquals("훈훈봇", results.get(3).getPersonaName());
    }

    @Test
    void getAutoPublishStatus_returnsConfigCountsAndSlotsForEveryPersona() {
        AssistantBotAutoPublishStatusDTO status = assistantBotService.getAutoPublishStatus();

        assertTrue(status.isEnabled());
        assertTrue(status.isAutoPublishEnabled());
        assertTrue(status.isAutoPublishCatchUpEnabled());
        assertEquals(4, status.getPersonas().size());
        assertEquals("프징징봇", status.getPersonas().get(0).getPersonaName());
        assertEquals("funboard", status.getPersonas().get(0).getBoardTitle());
        assertEquals(botProperties.getAutoPublishPostDailyLimit() + botProperties.getAutoPublishCatchUpPostRetrySlots(),
                status.getPersonas().get(0).getPostSlots().size());
        assertEquals(botProperties.getAutoPublishCommentDailyLimit(),
                status.getPersonas().get(0).getCommentSlots().size());
        assertTrue(status.getPersonas().get(0).getWaitingDetail() != null);
    }

    @Test
    void autoPublishPostDailyLimit_ignoresPreviousSkippedDrafts() throws Exception {
        botProperties.setAutoPublishPostDailyLimit(3);
        botProperties.setAutoPublishCommentDailyLimit(0);
        LocalDate date = LocalDate.of(2026, 3, 9);
        List<Integer> postSlots = botProperties.buildDailyAutoPublishSlots(date, "post", 3, "funboard", "프징징봇");
        while (postSlots.get(0) <= 60) {
            date = date.plusDays(1);
            postSlots = botProperties.buildDailyAutoPublishSlots(date, "post", 3, "funboard", "프징징봇");
        }
        int firstPostSlot = postSlots.get(0);
        LocalTime slotTime = LocalTime.of(firstPostSlot / 60, firstPostSlot % 60);
        ZoneId zone = ZoneId.of("Asia/Seoul");
        Clock slotClock = Clock.fixed(ZonedDateTime.of(date, slotTime, zone).toInstant(), zone);
        AssistantBotService service = new AssistantBotService(
                botProperties,
                new AssistantProperties(),
                boardService,
                boardMapper,
                assistantBotMapper,
                geminiClient,
                new ObjectMapper(),
                slotClock
        );

        LocalDateTime since = date.atStartOfDay();
        LocalDateTime minuteStart = LocalDateTime.of(date, slotTime);
        LocalDateTime recoverySince = minuteStart.minusMinutes(botProperties.getAutoPublishCatchUpRecoveryCooldownMinutes());
        AtomicReference<AssistantBotHistoryDTO> insertedHistory = new AtomicReference<>();
        doAnswer(invocation -> {
            AssistantBotHistoryDTO history = invocation.getArgument(0);
            history.setId(77L);
            insertedHistory.set(history);
            return null;
        }).when(assistantBotMapper).insertHistory(any(AssistantBotHistoryDTO.class));

        when(assistantBotMapper.countPublishedSinceByMode("프징징봇", "funboard", "post", since)).thenReturn(0);
        lenient().when(assistantBotMapper.countGeneratedSinceByMode("프징징봇", "funboard", "post", since)).thenReturn(1);
        when(assistantBotMapper.countGeneratedSinceByMode("프징징봇", "funboard", "post", minuteStart)).thenReturn(0);
        when(assistantBotMapper.countGeneratedSinceByMode("프징징봇", "funboard", "post", recoverySince)).thenReturn(0);
        when(assistantBotMapper.countGeneratedSinceByMode("프징징봇", "funboard", "comment", since)).thenReturn(0);
        when(assistantBotMapper.countGeneratedSinceByMode("프징징봇", "funboard", "comment", minuteStart)).thenReturn(0);
        when(boardMapper.selectRecentPostsForBot("funboard", botProperties.getRecentPostLimit()))
                .thenReturn(Collections.emptyList());
        when(boardMapper.selectRecentCommentsForBot("funboard", null, botProperties.getRecentCommentLimit()))
                .thenReturn(Collections.emptyList());
        when(assistantBotMapper.selectRecentHistory("프징징봇", "funboard", botProperties.getRecentHistoryLimit()))
                .thenReturn(Collections.emptyList());
        when(geminiClient.generateAnswer(anyString(), anyInt(), anyString()))
                .thenReturn(validPostDraftJson());
        when(assistantBotMapper.selectHistoryById(77L)).thenAnswer(invocation -> insertedHistory.get());
        when(boardMapper.selectRecentPostsForBot("funboard", 5))
                .thenReturn(Collections.singletonList(post(701, "프징징봇", 0, "오늘 래더 한 판만 더 해야지")));

        AssistantBotService.AutoPublishResult result = service.autoPublishOnce("프징징봇");

        assertEquals("published", result.getOutcome());
        assertEquals("post", result.getMode());
        assertEquals(Integer.valueOf(701), result.getPublishedPostNum());
        verify(assistantBotMapper).countPublishedSinceByMode("프징징봇", "funboard", "post", since);
        verify(geminiClient).generateAnswer(anyString(), anyInt(), anyString());
    }

    @Test
    void recommendPostTopicLane_prefersUnderrepresentedTopic() {
        AssistantBotHistoryDTO whiningA = history("post", "밸런스징징", "프로토스 또 억까당함", "이건 좀 너무한 거 아니냐");
        AssistantBotHistoryDTO whiningB = history("post", "밸런스징징", "테란 사기 같음", "오늘도 토스만 서럽다");
        AssistantBotHistoryDTO starChat = history("post", "스타수다", "오랜만에 리플 보는데", "옛날 경기 보니까 재밌네");

        String recommended = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "recommendPostTopicLane",
                Arrays.asList(whiningA, whiningB, starChat),
                1
        );

        assertEquals("일상글", recommended);
    }

    @Test
    void findDuplicateIssue_rejectsRepeatedTitleOpeningAgainstRecentPosts() {
        BoardDTO recentPost = post(501, "다른유저", 0, "요즘 들어 자꾸 겜하다가 딴생각이 드네");

        Object duplicateCheck = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "findDuplicateIssue",
                "post",
                "요즘 들어 자꾸 책상 위에 잡동사니가 쌓이네",
                "오늘은 그냥 물건만 치우고 끝냈다",
                Collections.singletonList(recentPost),
                Collections.emptyList(),
                Collections.emptyList()
        );

        assertEquals(Boolean.TRUE, ReflectionTestUtils.getField(duplicateCheck, "duplicate"));
        assertEquals("최근 게시글 제목 첫머리 패턴이 겹칩니다.", ReflectionTestUtils.getField(duplicateCheck, "feedback"));
    }

    @Test
    void buildPrompt_includesPersonaSpecificPostStyleAndOpeningHints() {
        BoardDTO recentPostA = post(601, "테스터A", 0, "요즘 들어 자꾸 겜하다가 딴생각이 드네");
        BoardDTO recentPostB = post(602, "테스터B", 0, "다들 래더 돌리기 전에 의자 높이는 제대로 맞추고 하냐?");
        AssistantBotHistoryDTO recentHistory = history("post", "일상글", "오늘 점심에 먹은 김치찌개가 너무 짜서 뇌가 절여지는 줄 알았다", "점심 한 끼가 너무 셌다");

        String prompt = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "buildPrompt",
                persona("저묵묵봇"),
                "post",
                "funboard",
                null,
                Arrays.asList(recentPostA, recentPostB),
                Collections.emptyList(),
                Collections.singletonList(recentHistory),
                null,
                1,
                3
        );

        assertTrue(prompt.contains("게시글 제목은 짧고 툭 끊겨야 한다."));
        assertTrue(prompt.contains("최근 재사용 금지 오프닝 예시:"));
        assertTrue(prompt.contains("analysis.post_strategy"));
        assertTrue(prompt.contains("이번 추천 작성 전략:"));
        assertTrue(prompt.contains("요즘 들어 자꾸"));
    }

    @Test
    void validateCandidate_rejectsFreshPostWhenRecentTitleKeywordsRepeat() throws Exception {
        BoardDTO latestPost = post(701, "테스터A", 0, "요즘 래더 보면 다들 너무 급하게 게임하는 거 같음");
        String rawJson = "{\"analysis\":{\"topic\":\"잡담\",\"post_strategy\":\"fresh\",\"risk_notes\":[]},"
                + "\"post\":{\"title\":\"요즘 래더 보면 너무 급하게 하게 됨\",\"body\":\"오늘도 손만 먼저 나가더라\"},"
                + "\"self_review\":{\"naturalness\":80,\"novelty\":80,\"engagement\":80,\"needs_revision\":false}}";

        Object candidate = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "validateCandidate",
                "post",
                new ObjectMapper().readTree(rawJson),
                Collections.singletonList(latestPost),
                Collections.emptyList(),
                Collections.emptyList()
        );

        assertEquals(Boolean.FALSE, ReflectionTestUtils.getField(candidate, "accepted"));
        assertEquals("신규 글인데 최신글 제목 단어를 너무 많이 재사용했습니다.", ReflectionTestUtils.getField(candidate, "feedback"));
    }

    @Test
    void validateCandidate_acceptsFreshPostWithLimitedRecentTitleKeywordOverlap() throws Exception {
        BoardDTO latestPost = post(702, "테스터A", 0, "퇴근길에 비 오니까 럴커 버로우 시키고 싶네");
        String rawJson = "{\"analysis\":{\"topic\":\"일상글\",\"post_strategy\":\"fresh\",\"risk_notes\":[]},"
                + "\"post\":{\"title\":\"비 오는 퇴근길에 손이 느리네\",\"body\":\"집 도착해서 한 판만 하려 했는데 손이 먼저 굳었다\"},"
                + "\"self_review\":{\"naturalness\":80,\"novelty\":80,\"engagement\":80,\"needs_revision\":false}}";

        Object candidate = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "validateCandidate",
                "post",
                new ObjectMapper().readTree(rawJson),
                Collections.singletonList(latestPost),
                Collections.emptyList(),
                Collections.emptyList()
        );

        assertEquals(Boolean.TRUE, ReflectionTestUtils.getField(candidate, "accepted"));
    }

    @Test
    void buildPrompt_forZergPersonaIncludesZergPointOfViewForGameTalk() {
        String prompt = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "buildPrompt",
                persona("저묵묵봇"),
                "post",
                "funboard",
                null,
                Collections.singletonList(post(801, "테스터A", 0, "래더에서 뮤탈 견제 막기 너무 빡세네")),
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                1,
                3
        );

        assertTrue(prompt.contains("게임 관련 이야기에서는 반드시 저그 유저 시점으로 보고"));
    }

    @Test
    void buildCommentInteractionRule_forZergPersonaUsesZergLensOnGameTalk() {
        BoardDTO targetPost = post(901, "테스터A", 0, "테란 배슬 뜨면 저그 너무 힘든 거 아니냐");
        targetPost.setContent("운영 말리는 느낌이 심하다");

        String rule = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "buildCommentInteractionRule",
                persona("저묵묵봇"),
                targetPost
        );

        assertTrue(rule.contains("저그 유저 시점"));
        assertTrue(rule.contains("뮤탈"));
    }

    @Test
    void buildPrompt_forWarmPersonaIncludesPositiveBlessingStyle() {
        String prompt = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "buildPrompt",
                persona("훈훈봇"),
                "post",
                "funboard",
                null,
                Collections.singletonList(post(902, "테스터A", 0, "오늘 래더 한 판 이겨서 기분 좋다")),
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                1,
                3
        );

        assertTrue(prompt.contains("짧은 응원"));
        assertTrue(prompt.contains("스타크래프트 얘기뿐 아니라"));
        assertTrue(prompt.contains("과장된 미담체는 피하라"));
        assertTrue(prompt.contains("전혀 무관한 일상 소재"));
    }

    @Test
    void buildPrompt_forTebpeonPersonaIncludesSentenceRangeRule() {
        String prompt = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "buildPrompt",
                persona("테뻔뻔봇"),
                "post",
                "funboard",
                null,
                Collections.singletonList(post(911, "테스터A", 0, "테란이 좀 잘하는 것 같긴 함")),
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                1,
                3
        );

        assertTrue(prompt.contains("게시글은 1~5문장, 댓글은 1~2문장으로 쓴다."));
    }

    @Test
    void buildPrompt_forPrzingPersonaIncludesSentenceRangeRule() {
        String prompt = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "buildPrompt",
                persona("프징징봇"),
                "comment",
                "funboard",
                post(912, "테스터A", 0, "오늘은 좀 억까가 심하네"),
                Collections.emptyList(),
                Arrays.asList(comment("다른유저", 1, "그럴 수 있지")),
                Collections.emptyList(),
                null,
                1,
                3
        );

        assertTrue(prompt.contains("게시글은 1~5문장, 댓글은 1~2문장으로 쓴다."));
    }

    @Test
    void buildPrompt_forMugmukPersonaAddsSingleSentenceRule() {
        String prompt = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "buildPrompt",
                persona("저묵묵봇"),
                "comment",
                "funboard",
                post(910, "테스터A", 0, "오늘도 래더가 좀 답답하네"),
                Collections.emptyList(),
                Arrays.asList(comment("다른유저", 1, "그럴 수도 있지")),
                Collections.emptyList(),
                null,
                1,
                3
        );

        assertTrue(prompt.contains("제목, 본문, 댓글 모두 한 문장만 쓴다."));
    }

    @Test
    void buildPostForPublish_forMugmukPersonaKeepsOnlyFirstSentence() throws Exception {
        String rawJson = "{\"post\":{\"title\":\"오늘은 짧게 간다. 두번째는 안 쓴다.\",\"body\":\"한 문장만 남긴다. 이건 잘라야 한다.\"}}";

        BoardDTO post = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "buildPostForPublish",
                persona("저묵묵봇"),
                new ObjectMapper().readTree(rawJson)
        );

        assertEquals("오늘은 짧게 간다.", post.getTitle());
        assertEquals("한 문장만 남긴다.", post.getContent());
    }

    @Test
    void buildPostForPublish_forTebpeonPersonaLimitsBodyToFiveSentences() throws Exception {
        String rawJson = "{\"post\":{\"title\":\"오늘은 좀 길다.\",\"body\":\"첫째 문장이다. 둘째 문장이다. 셋째 문장이다. 넷째 문장이다. 다섯째 문장이다. 여섯째 문장이다.\"}}";

        BoardDTO post = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "buildPostForPublish",
                persona("테뻔뻔봇"),
                new ObjectMapper().readTree(rawJson)
        );

        assertTrue(post.getContent().contains("첫째 문장이다."));
        assertTrue(post.getContent().contains("다섯째 문장이다."));
        assertTrue(!post.getContent().contains("여섯째 문장이다."));
    }

    @Test
    void safeCommentForPublish_forMugmukPersonaKeepsOnlyFirstSentence() {
        String content = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "safeCommentForPublish",
                persona("저묵묵봇"),
                "댓글도 한 문장만. 두번째는 제거한다."
        );

        assertEquals("댓글도 한 문장만.", content);
    }

    @Test
    void safeCommentForPublish_forPrzingPersonaLimitsToTwoSentences() {
        String content = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "safeCommentForPublish",
                persona("프징징봇"),
                "첫 문장이다. 둘째 문장이다. 셋째 문장이다."
        );

        assertEquals("첫 문장이다.\n둘째 문장이다.", content);
    }

    @Test
    void resolveModel_usesPersonaModelWhenConfigured() {
        AssistantBotProperties.PersonaProperties persona = persona("야옹봇");
        persona.setModel("gemini-2.5-flash-lite");

        String model = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "resolveModel",
                persona
        );

        assertEquals("gemini-2.5-flash-lite", model);
    }

    @Test
    void safeCommentForPublish_forMeowPersonaKeepsUpToThreeMeows() {
        String content = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "safeCommentForPublish",
                persona("야옹봇"),
                "야옹 야~~옹~~~~. 사람 말투는 제거한다. 야옹 야옹. 야~~옹~~~~."
        );

        assertEquals("야옹 야~~옹~~~~.\n야옹 야옹.\n야~~옹~~~~.", content);
    }

    @Test
    void toHtmlBody_forHealthPersonaLimitsToFiveSentencesWithDoubleBlankLines() {
        String content = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "toHtmlBody",
                persona("건강봇"),
                "첫 문장입니다. 둘째 문장입니다. 셋째 문장입니다. 넷째 문장입니다. 다섯째 문장입니다. 여섯째 문장입니다."
        );

        assertEquals("첫 문장입니다.<br><br><br>둘째 문장입니다.<br><br><br>셋째 문장입니다.<br><br><br>넷째 문장입니다.<br><br><br>다섯째 문장입니다.", content);
    }

    @Test
    void buildPrompt_forHealthPersonaRequiresHealthKnowledgeHelp() {
        String prompt = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "buildPrompt",
                persona("건강봇"),
                "post",
                "funboard",
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                1,
                3
        );

        assertTrue(prompt.contains("건강 상식 도움말"));
        assertTrue(prompt.contains("핵심 상식 -> 이유 -> 생활 속 적용 -> 주의/상담 기준"));
        assertTrue(prompt.contains("심호흡, 차 한잔, 물 마시기처럼 누구나 아는 한 줄 조언만으로 끝내면 실패"));
        assertTrue(prompt.contains("수면 위생"));
        assertTrue(prompt.contains("건강검진"));
        assertTrue(prompt.contains("스타 수다, 일반 잡담, 밸런스징징 순환 규칙은 건강봇에 적용하지 않는다."));
        assertFalse(prompt.contains("우선 추천 주제 결:"));
        assertFalse(prompt.contains("진심 60 / 드립 40"));
    }

    @Test
    void buildPrompt_forMeowPersonaKeepsOnlyMeowConceptRules() {
        String prompt = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "buildPrompt",
                persona("야옹봇"),
                "post",
                "funboard",
                null,
                Collections.singletonList(post(905, "테스터A", 0, "오늘 래더는 테란이 좀 뻔뻔하네")),
                Collections.emptyList(),
                Collections.singletonList(history("post", "야옹", "야옹 야~~옹~~~~", "야옹")),
                null,
                1,
                3
        );

        assertTrue(prompt.contains("울음 컨셉을 흔들림 없이 유지"));
        assertTrue(prompt.contains("야옹봇은 주제 균형을 적용하지 않는다."));
        assertFalse(prompt.contains("주제 풀은 '스타수다 / 일상글 / 잡담/뻘글 / 밸런스징징'"));
        assertFalse(prompt.contains("제목 오프닝 규칙:"));
        assertFalse(prompt.contains("최신글 연계/신규 전략 규칙:"));
        assertFalse(prompt.contains("구체적 장면이나 감정 한 조각"));
        assertFalse(prompt.contains("진심 60 / 드립 40"));
    }

    @Test
    void validateCandidate_forMeowPersonaAllowsIntentionalRepetition() throws Exception {
        String rawJson = "{\"analysis\":{\"topic\":\"야옹\",\"post_strategy\":\"fresh\",\"risk_notes\":[]},"
                + "\"post\":{\"title\":\"야옹 야~~옹~~~~\",\"body\":\"야옹 야~~옹~~~~. 야옹 야옹.\"},"
                + "\"self_review\":{\"naturalness\":80,\"novelty\":80,\"engagement\":80,\"needs_revision\":false}}";
        BoardDTO recentPost = post(907, "야옹봇", 0, "야옹 야~~옹~~~~");
        AssistantBotHistoryDTO recentHistory = history("post", "야옹", "야옹 야~~옹~~~~", "야옹 야~~옹~~~~");

        Object candidate = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "validateCandidate",
                persona("야옹봇"),
                "post",
                new ObjectMapper().readTree(rawJson),
                Collections.singletonList(recentPost),
                Collections.emptyList(),
                Collections.singletonList(recentHistory)
        );

        assertEquals(Boolean.TRUE, ReflectionTestUtils.getField(candidate, "accepted"));
    }

    @Test
    void buildPrompt_forHealthCommentDoesNotUseBotConflictRules() {
        BoardDTO targetPost = post(906, "테뻔뻔봇", 0, "손목이 좀 뻐근해도 래더는 해야지");
        targetPost.setContent("마우스 오래 잡으니까 손목이 살짝 찌릿하다");

        String prompt = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "buildPrompt",
                persona("건강봇"),
                "comment",
                "funboard",
                targetPost,
                Collections.emptyList(),
                Collections.singletonList(comment("테뻔뻔봇", 1, "테란은 손목도 실력으로 버틴다")),
                Collections.emptyList(),
                null,
                1,
                3
        );

        assertTrue(prompt.contains("다른 봇 글에 댓글 달 때도 시비를 걸지 말고 건강 상식"));
        assertFalse(prompt.contains("다른 봇 글에 댓글 달 때는 적당히 시비"));
        assertFalse(prompt.contains("종족 징징이나 자랑글"));
    }

    @Test
    void buildCommentInteractionRule_forHealthPersonaRequiresContextualKnowledge() {
        BoardDTO targetPost = post(904, "테스터A", 0, "요즘 앉아 있으면 허리가 뻐근하다");
        targetPost.setContent("퇴근하고 나면 허리랑 목이 같이 뻣뻣하다");

        String rule = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "buildCommentInteractionRule",
                persona("건강봇"),
                targetPost
        );

        assertTrue(rule.contains("건강상식"));
        assertTrue(rule.contains("단순 위로"));
        assertTrue(rule.contains("왜 도움이 되는지"));
        assertTrue(rule.contains("전문가 상담"));
    }

    @Test
    void buildCommentInteractionRule_forWarmPersonaEncouragesWithoutOverdoingIt() {
        BoardDTO targetPost = post(903, "테스터A", 0, "오늘 퇴근길에 비가 너무 많이 왔다");
        targetPost.setContent("우산이 있었는데도 신발이 다 젖어서 좀 난감했다");

        String rule = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "buildCommentInteractionRule",
                persona("훈훈봇"),
                targetPost
        );

        assertTrue(rule.contains("응원"));
        assertTrue(rule.contains("긍정적인 덕담"));
        assertTrue(rule.contains("설교"));
        assertTrue(rule.contains("전혀 무관한 일상이든"));
    }

    @Test
    void buildPrompt_commentModeIncludesThreadFlowAndSelfLatestStopRule() {
        BoardDTO targetPost = post(920, "일반유저", 2, "래더에서 운영 갈리면 멘탈도 같이 갈리네");
        targetPost.setContent("방금도 한 판 말렸다");

        String prompt = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "buildPrompt",
                persona("프징징봇"),
                "comment",
                "funboard",
                targetPost,
                Collections.emptyList(),
                Arrays.asList(comment("다른유저", 1, "이건 좀 서럽다"), comment("프징징봇", 2, "토스 입장에선 빡세지")),
                Collections.emptyList(),
                null,
                1,
                3
        );

        assertTrue(prompt.contains("댓글 스레드 힌트:"));
        assertTrue(prompt.contains("최신 댓글이 자기 댓글이면 should_reply=false"));
    }

    @Test
    void generateDraft_skipsCommentWhenLatestCommentIsPersona() throws Exception {
        AssistantBotDraftRequestDTO request = new AssistantBotDraftRequestDTO();
        request.setPersonaName("프징징봇");
        request.setBoardTitle("funboard");
        request.setMode("comment");
        request.setTargetPostNum(930);
        request.setRecentPostLimit(5);
        request.setRecentCommentLimit(5);

        BoardDTO targetPost = post(930, "일반유저", 2, "토스전 흐름이 너무 답답하네");
        targetPost.setContent("방금도 한 판 꼬였다");

        when(boardMapper.selectRecentPostsForBot("funboard", 5)).thenReturn(Collections.singletonList(targetPost));
        when(boardMapper.selectRecentCommentsForBot("funboard", 930, 5))
                .thenReturn(Arrays.asList(comment("다른유저", 1, "그럴 수도 있지"), comment("프징징봇", 2, "토스 입장에선 서럽다")));
        when(assistantBotMapper.selectRecentHistory("프징징봇", "funboard", botProperties.getRecentHistoryLimit()))
                .thenReturn(Collections.emptyList());
        when(boardMapper.readPost("funboard", 930)).thenReturn(targetPost);

        AssistantBotDraftResponseDTO response = assistantBotService.generateDraft(request);

        assertEquals("skipped", response.getStatus());
        assertEquals(Boolean.FALSE, response.getResult().path("reply").path("should_reply").asBoolean(true));
        verify(assistantBotMapper).insertHistory(any(AssistantBotHistoryDTO.class));
    }

    @Test
    void generateDraft_blocksWhenDailyGenerateCallLimitExceeded() throws Exception {
        botProperties.setDailyGenerateCallLimit(1);
        botProperties.setMaxGenerateAttempts(1);
        AssistantBotDraftRequestDTO request = postDraftRequest();

        when(boardMapper.selectRecentPostsForBot("funboard", botProperties.getRecentPostLimit()))
                .thenReturn(Collections.emptyList());
        when(boardMapper.selectRecentCommentsForBot("funboard", null, botProperties.getRecentCommentLimit()))
                .thenReturn(Collections.emptyList());
        when(assistantBotMapper.selectRecentHistory("프징징봇", "funboard", botProperties.getRecentHistoryLimit()))
                .thenReturn(Collections.emptyList());
        when(geminiClient.generateAnswer(anyString(), anyInt(), anyString()))
                .thenReturn(validPostDraftJson());

        AssistantBotDraftResponseDTO first = assistantBotService.generateDraft(request);
        AssistantBotDraftResponseDTO second = assistantBotService.generateDraft(request);

        assertEquals("draft", first.getStatus());
        assertTrue(second.getError().contains("일일 호출 한도"));
        verify(geminiClient, times(1)).generateAnswer(anyString(), anyInt(), anyString());
    }

    @Test
    void autoPublishDraftGeneration_usesSingleAttemptByDefault() throws Exception {
        botProperties.setMaxGenerateAttempts(3);
        botProperties.setAutoPublishMaxGenerateAttempts(1);
        AssistantBotDraftRequestDTO request = postDraftRequest();

        when(boardMapper.selectRecentPostsForBot("funboard", botProperties.getRecentPostLimit()))
                .thenReturn(Collections.emptyList());
        when(boardMapper.selectRecentCommentsForBot("funboard", null, botProperties.getRecentCommentLimit()))
                .thenReturn(Collections.emptyList());
        when(assistantBotMapper.selectRecentHistory("프징징봇", "funboard", botProperties.getRecentHistoryLimit()))
                .thenReturn(Collections.emptyList());
        when(geminiClient.generateAnswer(anyString(), anyInt(), anyString()))
                .thenReturn("{invalid json");

        AssistantBotDraftResponseDTO response = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "generateDraftInternal",
                request,
                botProperties.getAutoPublishMaxGenerateAttempts(),
                true
        );

        assertTrue(response.getError().contains("초안 품질 기준"));
        verify(geminiClient, times(1)).generateAnswer(anyString(), anyInt(), anyString());
    }

    @Test
    void isPublishMinute_onlyReturnsTrueAtExactRandomMinuteWhenCatchUpDisabled() {
        botProperties.setAutoPublishCatchUpEnabled(false);
        LocalDate date = LocalDate.of(2026, 3, 9);
        List<Integer> postSlots = botProperties.buildDailyAutoPublishSlots(date, "post", 3, "funboard", "프징징봇");
        int firstPostSlot = postSlots.stream()
                .filter(slot -> slot != null && slot > 0)
                .findFirst()
                .orElse(postSlots.get(0));
        int laterMinute = firstPostSlot == 1439 ? firstPostSlot : firstPostSlot + 1;
        int beforeSlotMinute = Math.max(0, firstPostSlot - 1);

        Boolean exactMinute = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "isPublishMinute",
                postSlots,
                firstPostSlot,
                0,
                0
        );

        Boolean catchUpMinute = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "isPublishMinute",
                postSlots,
                laterMinute,
                0,
                0
        );

        Boolean beforeSlot = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "isPublishMinute",
                postSlots,
                beforeSlotMinute,
                0,
                0
        );

        Boolean alreadyPublished = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "isPublishMinute",
                postSlots,
                laterMinute,
                1,
                0
        );

        assertEquals(Boolean.TRUE, exactMinute);
        assertEquals(Boolean.FALSE, catchUpMinute);
        assertEquals(Boolean.FALSE, beforeSlot);
        assertEquals(Boolean.FALSE, alreadyPublished);
    }

    @Test
    void isPublishMinute_reschedulesMissedPostToFutureCatchUpSlot() {
        botProperties.setAutoPublishCatchUpEnabled(true);
        botProperties.setAutoPublishPostDailyLimit(1);
        botProperties.setAutoPublishCatchUpPostRetrySlots(2);
        LocalDate date = LocalDate.of(2026, 3, 9);
        List<Integer> postSlots = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "resolveAutoPublishSlots",
                persona("프징징봇"),
                date,
                "post",
                botProperties.getAutoPublishPostDailyLimit()
        );
        int firstPostSlot = postSlots.get(0);
        int firstRetrySlot = postSlots.get(1);
        int nonScheduledMinute = findNonSlotMinuteAfter(postSlots, firstPostSlot);

        Boolean immediateRetry = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "isPublishMinute",
                postSlots,
                nonScheduledMinute,
                0,
                0,
                botProperties.getAutoPublishPostDailyLimit()
        );

        Boolean retrySlot = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "isPublishMinute",
                postSlots,
                firstRetrySlot,
                0,
                0,
                botProperties.getAutoPublishPostDailyLimit()
        );

        Boolean alreadyHandled = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "isPublishMinute",
                postSlots,
                firstRetrySlot,
                1,
                0,
                botProperties.getAutoPublishPostDailyLimit()
        );

        assertEquals(Boolean.FALSE, immediateRetry);
        assertEquals(Boolean.TRUE, retrySlot);
        assertEquals(Boolean.FALSE, alreadyHandled);
    }

    @Test
    void resolveDueAutoPublishCandidates_picksRetrySlotForMissedPost() {
        botProperties.setAutoPublishCatchUpEnabled(true);
        botProperties.setAutoPublishPostDailyLimit(1);
        botProperties.setAutoPublishCatchUpPostRetrySlots(2);
        LocalDate date = LocalDate.of(2026, 3, 9);
        AssistantBotProperties.PersonaProperties persona = persona("프징징봇");
        List<Integer> postSlots = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "resolveAutoPublishSlots",
                persona,
                date,
                "post",
                botProperties.getAutoPublishPostDailyLimit()
        );
        int firstRetrySlot = postSlots.get(1);

        List<?> dueAtRetrySlot = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "resolveDueAutoPublishCandidates",
                persona,
                date,
                LocalDateTime.of(date, java.time.LocalTime.of(firstRetrySlot / 60, firstRetrySlot % 60)).toLocalTime(),
                0,
                botProperties.getAutoPublishCommentDailyLimit(),
                0,
                0,
                0
        );

        List<?> alreadyHandled = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "resolveDueAutoPublishCandidates",
                persona,
                date,
                LocalDateTime.of(date, java.time.LocalTime.of(firstRetrySlot / 60, firstRetrySlot % 60)).toLocalTime(),
                1,
                botProperties.getAutoPublishCommentDailyLimit(),
                0,
                0,
                0
        );

        assertEquals(1, dueAtRetrySlot.size());
        assertTrue(alreadyHandled.isEmpty());
    }

    @Test
    void resolveDueAutoPublishCandidates_recoversOnceAfterLastPostSlotWhenCooldownClear() {
        botProperties.setAutoPublishCatchUpEnabled(true);
        botProperties.setAutoPublishPostDailyLimit(1);
        botProperties.setAutoPublishCatchUpPostRetrySlots(2);
        AssistantBotProperties.PersonaProperties persona = persona("프징징봇");
        LocalDate date = LocalDate.of(2026, 3, 9);
        List<Integer> postSlots = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "resolveAutoPublishSlots",
                persona,
                date,
                "post",
                botProperties.getAutoPublishPostDailyLimit()
        );
        int lastPostSlot = postSlots.get(postSlots.size() - 1);
        java.time.LocalTime recoveryTime = java.time.LocalTime.of((lastPostSlot + 1) / 60, (lastPostSlot + 1) % 60);

        List<?> recoveryDue = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "resolveDueAutoPublishCandidates",
                persona,
                date,
                recoveryTime,
                0,
                botProperties.getAutoPublishCommentDailyLimit(),
                0,
                0,
                0
        );

        List<?> blockedByCooldown = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "resolveDueAutoPublishCandidates",
                persona,
                date,
                recoveryTime,
                0,
                botProperties.getAutoPublishCommentDailyLimit(),
                0,
                0,
                1
        );

        assertEquals(1, recoveryDue.size());
        assertTrue(blockedByCooldown.isEmpty());
    }

    @Test
    void isPublishMinute_blocksDuplicatePublishWithinSameMinute() {
        LocalDate date = LocalDate.of(2026, 3, 9);
        List<Integer> postSlots = botProperties.buildDailyAutoPublishSlots(date, "post", 3, "funboard", "프징징봇");
        int firstPostSlot = postSlots.get(0);

        Boolean allowed = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "isPublishMinute",
                postSlots,
                firstPostSlot,
                0,
                1,
                1
        );

        assertEquals(Boolean.FALSE, allowed);
    }

    private BoardDTO post(int postNum, String writer, int commentCount) {
        return post(postNum, writer, commentCount, null);
    }

    private BoardDTO post(int postNum, String writer, int commentCount, String title) {
        BoardDTO post = new BoardDTO();
        post.setPostNum(postNum);
        post.setWriter(writer);
        post.setCommentCount(commentCount);
        post.setTitle(title);
        return post;
    }

    private CommentDTO comment(String nickname) {
        CommentDTO comment = new CommentDTO();
        comment.setNickname(nickname);
        return comment;
    }

    private CommentDTO comment(String nickname, int commentNum) {
        CommentDTO comment = comment(nickname);
        comment.setCommentNum(commentNum);
        return comment;
    }

    private CommentDTO comment(String nickname, int commentNum, String content) {
        CommentDTO comment = comment(nickname, commentNum);
        comment.setContent(content);
        return comment;
    }

    private AssistantBotHistoryDTO history(String mode, String topic, String title, String body) {
        AssistantBotHistoryDTO history = new AssistantBotHistoryDTO();
        history.setGenerationMode(mode);
        history.setTopic(topic);
        history.setDraftTitle(title);
        history.setDraftBody(body);
        return history;
    }

    private AssistantBotProperties.PersonaProperties persona(String name) {
        AssistantBotProperties.PersonaProperties persona = new AssistantBotProperties.PersonaProperties();
        persona.setName(name);
        persona.setBoardTitle("funboard");
        return persona;
    }

    private AssistantBotDraftRequestDTO postDraftRequest() {
        AssistantBotDraftRequestDTO request = new AssistantBotDraftRequestDTO();
        request.setPersonaName("프징징봇");
        request.setBoardTitle("funboard");
        request.setMode("post");
        request.setRecentPostLimit(botProperties.getRecentPostLimit());
        request.setRecentCommentLimit(botProperties.getRecentCommentLimit());
        return request;
    }

    private String validPostDraftJson() {
        return "{\"analysis\":{\"topic\":\"잡담\",\"post_strategy\":\"fresh\",\"risk_notes\":[]},"
                + "\"post\":{\"title\":\"오늘 래더 한 판만 더 해야지\",\"body\":\"말은 한 판인데 또 손이 간다\"},"
                + "\"self_review\":{\"naturalness\":90,\"novelty\":90,\"engagement\":90,\"needs_revision\":false}}";
    }

    private int findNonSlotMinuteAfter(List<Integer> slots, int startMinute) {
        int endMinute = slots.get(slots.size() - 1);
        for (int minute = startMinute + 1; minute < endMinute; minute++) {
            if (!slots.contains(minute)) {
                return minute;
            }
        }
        throw new IllegalStateException("non-slot minute not found after " + startMinute);
    }

    private static final class FixedRandom extends Random {
        private final double nextDoubleValue;
        private final int nextIntValue;

        private FixedRandom(double nextDoubleValue, int nextIntValue) {
            this.nextDoubleValue = nextDoubleValue;
            this.nextIntValue = nextIntValue;
        }

        @Override
        public double nextDouble() {
            return nextDoubleValue;
        }

        @Override
        public int nextInt(int bound) {
            if (bound <= 0) {
                throw new IllegalArgumentException("bound must be positive");
            }
            return Math.floorMod(nextIntValue, bound);
        }
    }

    private static final class RecordingAssistantBotService extends AssistantBotService {
        private final Map<String, AssistantBotService.AutoPublishResult> resultsByPersona;
        private final java.util.ArrayList<String> visitedPersonas = new java.util.ArrayList<>();

        private RecordingAssistantBotService(AssistantBotProperties botProperties,
                                             AssistantProperties assistantProperties,
                                             BoardService boardService,
                                             BoardMapper boardMapper,
                                             AssistantBotMapper assistantBotMapper,
                                             GeminiClient geminiClient,
                                             ObjectMapper objectMapper,
                                             Clock clock,
                                             Map<String, AssistantBotService.AutoPublishResult> resultsByPersona) {
            super(botProperties, assistantProperties, boardService, boardMapper, assistantBotMapper, geminiClient, objectMapper, clock);
            this.resultsByPersona = resultsByPersona;
        }

        @Override
        AssistantBotService.AutoPublishResult autoPublishOnce(String personaName) {
            visitedPersonas.add(personaName);
            AssistantBotService.AutoPublishResult result = resultsByPersona.get(personaName);
            return result == null
                    ? AssistantBotService.AutoPublishResult.skipped(personaName, "no_due_candidate")
                    : result;
        }

        private List<String> getVisitedPersonas() {
            return visitedPersonas;
        }
    }
}
