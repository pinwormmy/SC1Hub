package com.sc1hub.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.assistant.config.AssistantBotProperties;
import com.sc1hub.assistant.config.AssistantProperties;
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
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
        botProperties.setPersonas(Arrays.asList(persona("프징징봇"), persona("테뻔뻔봇"), persona("저묵묵봇")));

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
    void isPublishMinute_onlyReturnsTrueAtExactRandomMinute() {
        LocalDate date = LocalDate.of(2026, 3, 9);
        List<Integer> postSlots = botProperties.buildDailyAutoPublishSlots(date, "post", 3, "funboard", "프징징봇");
        int firstPostSlot = postSlots.get(0);
        int nonExactMinute = firstPostSlot == 1439 ? 1438 : firstPostSlot + 1;

        Boolean exactMinute = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "isPublishMinute",
                postSlots,
                firstPostSlot,
                0,
                0
        );

        Boolean otherMinute = ReflectionTestUtils.invokeMethod(
                assistantBotService,
                "isPublishMinute",
                postSlots,
                nonExactMinute,
                0,
                0
        );

        assertEquals(Boolean.TRUE, exactMinute);
        assertEquals(Boolean.FALSE, otherMinute);
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
        BoardDTO post = new BoardDTO();
        post.setPostNum(postNum);
        post.setWriter(writer);
        post.setCommentCount(commentCount);
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
