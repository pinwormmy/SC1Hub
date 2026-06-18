package com.sc1hub.file.controller;

import com.sc1hub.common.config.WebConfig;
import com.sc1hub.common.interceptor.CanonicalInterceptor;
import com.sc1hub.file.util.UploadedImageFileNameUtil;
import com.sc1hub.visitor.service.VisitorCountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.test.context.web.WebAppConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = UploadControllerWebMvcTest.TestConfig.class)
class UploadControllerWebMvcTest {

    @Configuration
    @EnableWebMvc
    static class TestConfig {

        @Bean
        UploadController uploadController() {
            return new UploadController();
        }

        @Bean
        CanonicalInterceptor canonicalInterceptor() {
            return new CanonicalInterceptor();
        }

        @Bean
        VisitorCountService visitorCountService() {
            return Mockito.mock(VisitorCountService.class);
        }

        @Bean
        WebConfig webConfig(VisitorCountService visitorCountService, CanonicalInterceptor canonicalInterceptor) {
            return new WebConfig(visitorCountService, canonicalInterceptor);
        }
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UploadController controller;

    @Autowired
    private VisitorCountService visitorCountService;

    @TempDir
    Path tempDir;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        ReflectionTestUtils.setField(controller, "uploadPath", tempDir.toString());
        ReflectionTestUtils.setField(controller, "imageUploadPath", "");
        ReflectionTestUtils.setField(controller, "resolvedUploadPath", null);
        ReflectionTestUtils.setField(controller, "resolvedImageUploadPath", null);
        given(visitorCountService.getTodayCount()).willReturn(0);
        given(visitorCountService.getTotalCount()).willReturn(0);
    }

    @Test
    void imgRoute_usesControllerRecoveryForStoredRedirectPath() throws Exception {
        String uid = "81469f62-87c3-4b41-8832-eb089fa50414";
        byte[] imageBytes = new byte[] { 9, 8, 7, 6 };
        Files.write(tempDir.resolve(uid + "_뮤탈과 바이오닉 심슨버전.jpg"), imageBytes);
        String storedFileName = UploadedImageFileNameUtil.toStoredFileName("뮤탈과 바이오닉 심슨버전.jpg");

        mockMvc.perform(get("/uploadedImg/{imageName}", uid + "_" + storedFileName))
                .andExpect(status().isOk())
                .andExpect(content().bytes(imageBytes));
    }
}
