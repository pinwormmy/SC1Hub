package com.sc1hub.buildtimer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.buildtimer.dto.BuildTimerBuildDTO;
import com.sc1hub.buildtimer.service.BuildTimerService;
import com.sc1hub.common.interceptor.CanonicalInterceptor;
import com.sc1hub.seo.SeoMetadataService;
import com.sc1hub.seo.SeoUrlPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class BuildTimerControllerTest {

    private MockMvc mockMvc;
    private BuildTimerService buildTimerService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        buildTimerService = new BuildTimerService(objectMapper);
        SeoMetadataService seoMetadataService = new SeoMetadataService(objectMapper);
        BuildTimerController controller = new BuildTimerController(buildTimerService, seoMetadataService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(new CanonicalInterceptor(new SeoUrlPolicy()))
                .build();
    }

    @Test
    void showBuildTimer_rendersViewWithCatalogAndSeoMetadata() throws Exception {
        MvcResult result = mockMvc.perform(get("/build-timer"))
                .andExpect(status().isOk())
                .andExpect(view().name("buildTimer"))
                .andExpect(model().attributeExists("builds", "buildsJson", "pageTitle", "metaDescription",
                        "socialImage", "structuredDataJson"))
                .andReturn();

        @SuppressWarnings("unchecked")
        List<BuildTimerBuildDTO> builds = (List<BuildTimerBuildDTO>) result.getModelAndView().getModel().get("builds");
        assertNotNull(builds);
        assertFalse(builds.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> builds.get(0).getSteps().clear());
        assertTrue(String.valueOf(result.getModelAndView().getModel().get("structuredDataJson"))
                .contains("https://sc1hub.com/build-timer"));
        assertTrue(String.valueOf(result.getModelAndView().getModel().get("pageTitle")).contains("빌드오더"));
    }
}
