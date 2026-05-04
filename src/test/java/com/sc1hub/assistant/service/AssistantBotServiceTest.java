package com.sc1hub.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.assistant.config.AssistantBotProperties;
import com.sc1hub.assistant.config.AssistantProperties;
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
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.spy;
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
        AssistantBotService spyService = spy(assistantBotService);
        doReturn(AssistantBotService.AutoPublishResult.published("프징징봇", "post", 1L, 101, "/boards/funboard/readPost?postNum=101"))
                .when(spyService).autoPublishOnce("프징징봇");
        doReturn(AssistantBotService.AutoPublishResult.skipped("테뻔뻔봇", "no_due_candidate"))
                .when(spyService).autoPublishOnce("테뻔뻔봇");
        doReturn(AssistantBotService.AutoPublishResult.failed("저묵묵봇", "draft_error:test"))
                .when(spyService).autoPublishOnce("저묵묵봇");
        doReturn(AssistantBotService.AutoPublishResult.published("훈훈봇", "comment", 2L, 202, "/boards/funboard/readPost?postNum=202"))
                .when(spyService).autoPublishOnce("훈훈봇");

        AssistantBotService.AutoPublishResult result = spyService.autoPublishOnce();

        assertEquals("published", result.getOutcome());
        assertEquals("훈훈봇", result.getPersonaName());
        verify(spyService).autoPublishOnce("프징징봇");
        verify(spyService).autoPublishOnce("테뻔뻔봇");
        verify(spyService).autoPublishOnce("저묵묵봇");
        verify(spyService).autoPublishOnce("훈훈봇");
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

        assertTrue(prompt.contains("긍정적인 덕담"));
        assertTrue(prompt.contains("다양한 주제"));
        assertTrue(prompt.contains("과장된 미담체는 피하라"));
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
    void buildCommentInteractionRule_forWarmPersonaEncouragesWithoutOverdoingIt() {
        BoardDTO targetPost = post(903, "테스터A", 0, "오늘 게임 좀 잘 풀렸다");
        targetPost.setContent("오랜만에 연승해서 기분 좋네");

        String rule = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "buildCommentInteractionRule",
                persona("훈훈봇"),
                targetPost
        );

        assertTrue(rule.contains("응원"));
        assertTrue(rule.contains("긍정적인 덕담"));
        assertTrue(rule.contains("설교"));
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
    void isPublishMinute_onlyReturnsTrueAtExactRandomMinuteByDefault() {
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
    void isPublishMinute_catchesUpMissedSlotOnlyWhenEnabled() {
        botProperties.setAutoPublishCatchUpEnabled(true);
        LocalDate date = LocalDate.of(2026, 3, 9);
        List<Integer> postSlots = botProperties.buildDailyAutoPublishSlots(date, "post", 3, "funboard", "프징징봇");
        int firstPostSlot = postSlots.stream()
                .filter(slot -> slot != null && slot > 0)
                .findFirst()
                .orElse(postSlots.get(0));
        int laterMinute = firstPostSlot == 1439 ? firstPostSlot : firstPostSlot + 1;

        Boolean catchUpMinute = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "isPublishMinute",
                postSlots,
                laterMinute,
                0,
                0
        );

        assertEquals(Boolean.TRUE, catchUpMinute);
    }

    @Test
    void resolveDueAutoPublishCandidates_usesHandledCountForCatchUp() {
        botProperties.setAutoPublishCatchUpEnabled(true);
        LocalDate date = LocalDate.of(2026, 3, 9);
        LocalDateTime endOfDay = date.atTime(23, 59);
        AssistantBotProperties.PersonaProperties persona = persona("프징징봇");

        List<?> alreadyHandled = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "resolveDueAutoPublishCandidates",
                persona,
                date,
                endOfDay.toLocalTime(),
                botProperties.getAutoPublishPostDailyLimit(),
                botProperties.getAutoPublishCommentDailyLimit(),
                0,
                0
        );

        List<?> missingOnePost = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "resolveDueAutoPublishCandidates",
                persona,
                date,
                endOfDay.toLocalTime(),
                botProperties.getAutoPublishPostDailyLimit() - 1,
                botProperties.getAutoPublishCommentDailyLimit(),
                0,
                0
        );

        assertTrue(alreadyHandled.isEmpty());
        assertEquals(1, missingOnePost.size());
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
}
