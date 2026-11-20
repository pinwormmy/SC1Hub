package com.sc1hub.tools.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.common.interceptor.CanonicalInterceptor;
import com.sc1hub.seo.SeoMetadataService;
import com.sc1hub.seo.SeoUrlPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class KeyboardTestControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SeoMetadataService seoMetadataService = new SeoMetadataService(new ObjectMapper());
        KeyboardTestController controller = new KeyboardTestController(seoMetadataService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(new CanonicalInterceptor(new SeoUrlPolicy()))
                .build();
    }

    @Test
    void showKeyboardTest_rendersViewWithSeoMetadata() throws Exception {
        MvcResult result = mockMvc.perform(get("/keyboard-test"))
                .andExpect(status().isOk())
                .andExpect(view().name("keyboardTest"))
                .andExpect(model().attributeExists("pageTitle", "metaDescription", "socialImage",
                        "structuredDataJson"))
                .andReturn();

        assertTrue(String.valueOf(result.getModelAndView().getModel().get("structuredDataJson"))
                .contains("https://sc1hub.com/keyboard-test"));
        assertTrue(String.valueOf(result.getModelAndView().getModel().get("pageTitle")).contains("키보드"));
    }
}
