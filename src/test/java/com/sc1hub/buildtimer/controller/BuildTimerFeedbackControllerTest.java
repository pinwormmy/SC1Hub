package com.sc1hub.buildtimer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.buildtimer.dto.BuildTimerFeedbackDTO;
import com.sc1hub.buildtimer.mapper.BuildTimerFeedbackMapper;
import com.sc1hub.buildtimer.service.BuildTimerFeedbackService;
import com.sc1hub.buildtimer.service.BuildTimerService;
import com.sc1hub.common.security.AttackContentDetector;
import com.sc1hub.common.security.DuplicateContentGuard;
import com.sc1hub.common.security.OffenderTracker;
import com.sc1hub.member.dto.MemberDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BuildTimerFeedbackControllerTest {

    private MockMvc mockMvc;
    private BuildTimerFeedbackMapper feedbackMapper;
    private OffenderTracker offenderTracker;

    @BeforeEach
    void setUp() {
        feedbackMapper = mock(BuildTimerFeedbackMapper.class);
        when(feedbackMapper.insertFeedback(any())).thenAnswer(invocation -> {
            BuildTimerFeedbackDTO dto = invocation.getArgument(0);
            dto.setId(101L);
            return 1;
        });
        BuildTimerService buildTimerService = new BuildTimerService(new ObjectMapper());
        BuildTimerFeedbackService feedbackService = new BuildTimerFeedbackService(feedbackMapper, buildTimerService);
        offenderTracker = mock(OffenderTracker.class);

        BuildTimerFeedbackController controller = new BuildTimerFeedbackController(
                feedbackService, new AttackContentDetector(), new DuplicateContentGuard(), offenderTracker);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void submitFeedback_acceptsGuestSubmissionAndStoresIt() throws Exception {
        String body = """
                {"category":"timing","buildId":"tvz-2barracks-academy","message":"앞마당  timing 안내가 실제보다 빨라요","contact":"guest@example.com"}
                """;

        mockMvc.perform(post("/api/build-timer/feedback")
                        .header("X-Forwarded-For", "203.0.113.55")
                        .header("User-Agent", "SC1HubTestBrowser/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.id", is(101)));

        ArgumentCaptor<BuildTimerFeedbackDTO> captor = ArgumentCaptor.forClass(BuildTimerFeedbackDTO.class);
        verify(feedbackMapper).insertFeedback(captor.capture());
        assertEquals("timing", captor.getValue().getCategory());
        assertEquals("tvz-2barracks-academy", captor.getValue().getBuildId());
        assertEquals("203.0.113.55", captor.getValue().getIp());
    }

    @Test
    void submitFeedback_recordsLoggedInMemberIdentity() throws Exception {
        MemberDTO member = new MemberDTO();
        member.setId("terran77");
        member.setNickName("테란유저");
        member.setGrade(1);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("member", member);

        String body = """
                {"category":"ux","buildId":"","message":"모바일에서 다음 단계 버튼이 크게 보여 좋습니다."}
                """;

        mockMvc.perform(post("/api/build-timer/feedback")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        ArgumentCaptor<BuildTimerFeedbackDTO> captor = ArgumentCaptor.forClass(BuildTimerFeedbackDTO.class);
        verify(feedbackMapper).insertFeedback(captor.capture());
        assertEquals("terran77", captor.getValue().getMemberId());
        assertEquals("테란유저", captor.getValue().getNickname());
    }

    @Test
    void submitFeedback_dropsHoneypotSilentlyWithoutPersisting() throws Exception {
        String body = """
                {"category":"general","message":"봇 스팸 테스트 메시지입니다","website":"https://spam.example"}
                """;

        mockMvc.perform(post("/api/build-timer/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        verify(feedbackMapper, never()).insertFeedback(any());
    }

    @Test
    void submitFeedback_rejectsAttackPayloadAndStrikesOffender() throws Exception {
        String body = """
                {"category":"bug","message":"<script>alert(1)</script> 테스트 공격입니다"}
                """;

        mockMvc.perform(post("/api/build-timer/feedback")
                        .header("X-Forwarded-For", "203.0.113.88")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());

        verify(feedbackMapper, never()).insertFeedback(any());
        verify(offenderTracker).attackDetected(eq("203.0.113.88"), eq(true), eq(null), eq(null), any());
    }

    @Test
    void submitFeedback_rejectsDuplicateWithinWindowAndShortMessages() throws Exception {
        String body = """
                {"category":"general","message":"같은 피드백을 연속으로 보내는 테스트"}
                """;

        mockMvc.perform(post("/api/build-timer/feedback")
                        .header("X-Forwarded-For", "203.0.113.99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/build-timer/feedback")
                        .header("X-Forwarded-For", "203.0.113.99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(post("/api/build-timer/feedback")
                        .header("X-Forwarded-For", "203.0.113.99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"general\",\"message\":\"짧음\"}"))
                .andExpect(status().isBadRequest());

        verify(feedbackMapper, times(1)).insertFeedback(any());
    }
}
