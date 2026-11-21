package com.sc1hub.buildtimer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.buildtimer.dto.BuildTimerFeedbackDTO;
import com.sc1hub.buildtimer.mapper.BuildTimerFeedbackMapper;
import com.sc1hub.buildtimer.service.BuildTimerFeedbackService;
import com.sc1hub.buildtimer.service.BuildTimerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BuildTimerFeedbackAdminControllerTest {

    private MockMvc mockMvc;
    private BuildTimerFeedbackMapper feedbackMapper;

    @BeforeEach
    void setUp() {
        feedbackMapper = mock(BuildTimerFeedbackMapper.class);
        BuildTimerService buildTimerService = new BuildTimerService(new ObjectMapper());
        BuildTimerFeedbackService feedbackService = new BuildTimerFeedbackService(feedbackMapper, buildTimerService);
        BuildTimerFeedbackAdminController controller = new BuildTimerFeedbackAdminController(feedbackService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void listFeedback_returnsItemsAndCounts() throws Exception {
        BuildTimerFeedbackDTO row = new BuildTimerFeedbackDTO();
        row.setId(15L);
        row.setBuildId("tvz-2barracks-academy");
        row.setBuildLabel("테저전 · 2배럭 아카데미");
        row.setCategory("timing");
        row.setMessage("스캔 타이밍 체크포인트가 좋아요");
        row.setNickname("마린");
        row.setStatus("NEW");
        row.setRegDate(LocalDateTime.of(2026, 11, 21, 11, 30));

        when(feedbackMapper.selectRecentFeedback(null, 50)).thenReturn(List.of(row));
        when(feedbackMapper.countFeedback("NEW")).thenReturn(1);
        when(feedbackMapper.countFeedback(null)).thenReturn(3);

        mockMvc.perform(get("/api/admin/build-timer/feedback"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newCount", is(1)))
                .andExpect(jsonPath("$.totalCount", is(3)))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].regDate", is("2026-11-21 11:30")))
                .andExpect(jsonPath("$.items[0].status", is("NEW")));
    }

    @Test
    void updateStatus_handles200And400And404() throws Exception {
        when(feedbackMapper.updateStatus(15L, "DONE")).thenReturn(1);
        when(feedbackMapper.updateStatus(999L, "DONE")).thenReturn(0);

        mockMvc.perform(post("/api/admin/build-timer/feedback/15/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"done\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("DONE")));

        mockMvc.perform(post("/api/admin/build-timer/feedback/15/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/build-timer/feedback/999/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteFeedback_handles200And404() throws Exception {
        when(feedbackMapper.deleteFeedback(15L)).thenReturn(1);
        when(feedbackMapper.deleteFeedback(999L)).thenReturn(0);

        mockMvc.perform(delete("/api/admin/build-timer/feedback/15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        mockMvc.perform(delete("/api/admin/build-timer/feedback/999"))
                .andExpect(status().isNotFound());
    }
}
