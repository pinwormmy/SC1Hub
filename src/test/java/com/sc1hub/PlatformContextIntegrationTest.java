package com.sc1hub;

import org.junit.jupiter.api.Test;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.mybatis.spring.boot.autoconfigure.MybatisProperties;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        "sc1hub.chat.enabled=false"
})
@ActiveProfiles("test")
class PlatformContextIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Autowired
    private MybatisProperties mybatisProperties;

    @Autowired
    private Environment environment;

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
    }
}
