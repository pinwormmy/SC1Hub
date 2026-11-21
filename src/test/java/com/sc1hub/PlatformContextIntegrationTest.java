package com.sc1hub;

import com.sc1hub.buildtimer.controller.BuildTimerFeedbackAdminController;
import com.sc1hub.buildtimer.controller.BuildTimerFeedbackController;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.mybatis.spring.boot.autoconfigure.MybatisProperties;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sc1hub;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.task.scheduling.enabled=false",
        "path.upload.ck=/tmp/sc1hub-platform-test/upload",
        "path.upload.img=/tmp/sc1hub-platform-test/upload",
        "mail.smtp.port=2525",
        "mail.smtp.socketFactory.port=2525",
        "mail.smtp.auth=false",
        "mail.smtp.starttls.enable=false",
        "mail.smtp.starttls.required=false",
        "mail.smtp.socketFactory.fallback=true",
        "AdminMail.id=test@example.com",
        "AdminMail.password=unused",
        "sc1hub.assistant.enabled=false",
        "sc1hub.assistant.bot.enabled=false",
        "sc1hub.assistant.bot.autoPublishEnabled=false",
        "sc1hub.assistant.rag.enabled=false",
        "sc1hub.chat.enabled=false",
        "sc1hub.security.legacy-password-migration-enabled=false"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PlatformContextIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Autowired
    private MybatisProperties mybatisProperties;

    @Autowired
    private Environment environment;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void applicationContextStartsOnTheUpgradedPlatform() {
        assertNotNull(applicationContext.getBean(SC1HubApplication.class));
        assertEquals("classpath*:/mapper/*.xml", environment.getProperty("mybatis.mapper-locations"));
        assertNotNull(mybatisProperties.getMapperLocations(),
                "MyBatis mapper-locations must be bound from application.properties");
        assertEquals("classpath*:/mapper/*.xml", mybatisProperties.getMapperLocations()[0]);
        assertTrue(mybatisProperties.resolveMapperLocations().length > 0,
                "MyBatis mapper resources must resolve from the configured classpath pattern");
        assertTrue(sqlSessionFactory.getConfiguration().hasStatement(
                "com.sc1hub.chat.mapper.ChatMapper.selectMaxId", false));
        assertTrue(sqlSessionFactory.getConfiguration().hasStatement(
                "com.sc1hub.buildtimer.mapper.BuildTimerFeedbackMapper.insertFeedback", false));
        assertNotNull(applicationContext.getBean(BuildTimerFeedbackController.class));
        assertNotNull(applicationContext.getBean(BuildTimerFeedbackAdminController.class));
    }

    @Test
    void faviconIsServedWithCacheControlAndMissingStaticReturns404() throws Exception {
        MvcResult favicon = mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=2592000")))
                .andReturn();
        assertTrue(favicon.getResponse().getContentAsByteArray().length > 0);

        mockMvc.perform(get("/definitely-missing-static-path-test"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Robots-Tag", "noindex,nofollow,noarchive"));
    }
}
