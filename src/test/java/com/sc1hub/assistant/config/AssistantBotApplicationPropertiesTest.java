package com.sc1hub.assistant.config;

import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class AssistantBotApplicationPropertiesTest {

    @Test
    void packagedPersonasExcludeHoonHoonBotAndKeepGosuOverrides() throws Exception {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(
                Paths.get("src/main/resources/application.properties"),
                StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        List<String> expectedNames = Arrays.asList(
                "프징징봇",
                "테뻔뻔봇",
                "저묵묵봇",
                "건강봇",
                "고수봇"
        );
        for (int index = 0; index < expectedNames.size(); index += 1) {
            assertEquals(expectedNames.get(index),
                    properties.getProperty("sc1hub.assistant.bot.personas[" + index + "].name"));
        }

        assertNull(properties.getProperty("sc1hub.assistant.bot.personas[5].name"));
        assertNull(properties.getProperty("sc1hub.assistant.bot.personas[4].provider"));
        // 고수봇도 공통 모델/추론 강도를 물려받는다. 따로 지정하면 그 설정이 우선한다.
        assertNull(properties.getProperty("sc1hub.assistant.bot.personas[4].model"));
        assertNull(properties.getProperty("sc1hub.assistant.bot.personas[4].reasoningEffort"));
        assertEquals("3000", properties.getProperty("sc1hub.assistant.bot.personas[4].maxOutputTokens"));
        assertEquals("3", properties.getProperty("sc1hub.assistant.bot.personas[4].autoPublishChatDailyLimit"));
        assertEquals("7", properties.getProperty("sc1hub.assistant.bot.personas[4].autoPublishChatMaxAttemptsPerDay"));
        assertFalse(properties.containsValue("훈훈봇"));
    }

    @Test
    void packagedChatBotsUseOpenAiLunaAtMaxReasoning() throws Exception {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(
                Paths.get("src/main/resources/application.properties"),
                StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        assertEquals("gpt-5.6-luna", properties.getProperty("sc1hub.assistant.bot.model"));
        assertEquals("max", properties.getProperty("sc1hub.assistant.bot.reasoningEffort"));
    }

    @Test
    void packagedSearchUsesOpenAiLunaAtHighReasoning() throws Exception {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(
                Paths.get("src/main/resources/application.properties"),
                StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        assertEquals("openai", properties.getProperty("sc1hub.assistant.searchProvider"));
        assertEquals("gpt-5.6-luna", properties.getProperty("sc1hub.openai.searchModel"));
        assertEquals("high", properties.getProperty("sc1hub.openai.searchReasoningEffort"));
    }
}
