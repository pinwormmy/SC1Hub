package com.sc1hub.buildtimer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.buildtimer.dto.BuildTimerBuildDTO;
import com.sc1hub.buildtimer.dto.BuildTimerFeedbackDTO;
import com.sc1hub.buildtimer.dto.BuildTimerFeedbackRequestDTO;
import com.sc1hub.buildtimer.mapper.BuildTimerFeedbackMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildTimerFeedbackServiceTest {

    private BuildTimerFeedbackMapper feedbackMapper;
    private BuildTimerService buildTimerService;
    private BuildTimerFeedbackService feedbackService;

    @BeforeEach
    void setUp() {
        feedbackMapper = mock(BuildTimerFeedbackMapper.class);
        buildTimerService = new BuildTimerService(new ObjectMapper());
        feedbackService = new BuildTimerFeedbackService(feedbackMapper, buildTimerService);
        when(feedbackMapper.insertFeedback(any())).thenAnswer(invocation -> {
            BuildTimerFeedbackDTO dto = invocation.getArgument(0);
            dto.setId(42L);
            return 1;
        });
    }

    @Test
    void submitFeedback_resolvesKnownBuildAndDefaultsBlankCategory() {
        BuildTimerBuildDTO firstBuild = buildTimerService.getBuilds().get(0);
        BuildTimerFeedbackRequestDTO req = new BuildTimerFeedbackRequestDTO();
        req.setBuildId(firstBuild.getId());
        req.setCategory("  ");
        req.setMessage("  실게임보다 5초 정도 빨리 울립니다. 확인 부탁해요  ");
        req.setContact("  tester@sc1hub.com ");

        BuildTimerFeedbackDTO saved = feedbackService.submitFeedback(
                req, "user1", "테스터", "203.0.113.10, 10.0.0.1", "Mozilla/5.0\nTestAgent");

        assertEquals(42L, saved.getId());
        assertEquals(firstBuild.getId(), saved.getBuildId());
        assertEquals(firstBuild.getMatchup() + " · " + firstBuild.getName(), saved.getBuildLabel());
        assertEquals("general", saved.getCategory());
        assertEquals("실게임보다 5초 정도 빨리 울립니다. 확인 부탁해요", saved.getMessage());
        assertEquals("tester@sc1hub.com", saved.getContact());
        assertEquals("203.0.113.10", saved.getIp());
        assertEquals("Mozilla/5.0TestAgent", saved.getUserAgent());
        assertEquals(BuildTimerFeedbackService.STATUS_NEW, saved.getStatus());

        ArgumentCaptor<BuildTimerFeedbackDTO> captor = ArgumentCaptor.forClass(BuildTimerFeedbackDTO.class);
        verify(feedbackMapper).insertFeedback(captor.capture());
        assertEquals("user1", captor.getValue().getMemberId());
    }

    @Test
    void submitFeedback_fallsBackToCommonLabelForUnknownBuild() {
        BuildTimerFeedbackRequestDTO req = new BuildTimerFeedbackRequestDTO();
        req.setBuildId("unknown-build-id");
        req.setCategory("build_request");
        req.setMessage("저프전 9드론 빌드도 추가해 주세요.");

        BuildTimerFeedbackDTO saved = feedbackService.submitFeedback(req, null, null, null, null);

        assertNull(saved.getBuildId());
        assertEquals(BuildTimerFeedbackService.COMMON_BUILD_LABEL, saved.getBuildLabel());
        assertEquals("build_request", saved.getCategory());
    }

    @Test
    void submitFeedback_validatesMessageBoundsCategoryAndContact() {
        BuildTimerFeedbackRequestDTO tooShort = new BuildTimerFeedbackRequestDTO();
        tooShort.setCategory("timing");
        tooShort.setMessage("짧음");
        assertThrows(IllegalArgumentException.class,
                () -> feedbackService.submitFeedback(tooShort, null, null, null, null));

        BuildTimerFeedbackRequestDTO boundaryOk = new BuildTimerFeedbackRequestDTO();
        boundaryOk.setCategory("timing");
        boundaryOk.setMessage("다섯글자임");
        BuildTimerFeedbackDTO saved = feedbackService.submitFeedback(boundaryOk, null, null, null, null);
        assertEquals("다섯글자임", saved.getMessage());

        BuildTimerFeedbackRequestDTO tooLong = new BuildTimerFeedbackRequestDTO();
        tooLong.setCategory("timing");
        tooLong.setMessage("가".repeat(1001));
        assertThrows(IllegalArgumentException.class,
                () -> feedbackService.submitFeedback(tooLong, null, null, null, null));

        BuildTimerFeedbackRequestDTO badCategory = new BuildTimerFeedbackRequestDTO();
        badCategory.setCategory("invalid_cat");
        badCategory.setMessage("정상 길이의 피드백 내용");
        assertThrows(IllegalArgumentException.class,
                () -> feedbackService.submitFeedback(badCategory, null, null, null, null));

        BuildTimerFeedbackRequestDTO unsafeContact = new BuildTimerFeedbackRequestDTO();
        unsafeContact.setCategory("bug");
        unsafeContact.setMessage("정상 길이의 피드백 내용");
        unsafeContact.setContact("<bad@example.com>");
        assertThrows(IllegalArgumentException.class,
                () -> feedbackService.submitFeedback(unsafeContact, null, null, null, null));

        verify(feedbackMapper).insertFeedback(any());
    }

    @Test
    void adminMethods_validateStatusAndId() {
        when(feedbackMapper.updateStatus(7L, "REVIEWED")).thenReturn(1);
        assertTrue(feedbackService.updateStatus(7L, "reviewed"));
        verify(feedbackMapper).updateStatus(7L, "REVIEWED");

        assertThrows(IllegalArgumentException.class, () -> feedbackService.updateStatus(0L, "REVIEWED"));
        assertThrows(IllegalArgumentException.class, () -> feedbackService.updateStatus(7L, "BAD_STATUS"));
        assertThrows(IllegalArgumentException.class, () -> feedbackService.getRecentFeedback("BAD_STATUS", 10));
        assertThrows(IllegalArgumentException.class, () -> feedbackService.deleteFeedback(-1L));
        verify(feedbackMapper, never()).deleteFeedback(-1L);
    }
}
