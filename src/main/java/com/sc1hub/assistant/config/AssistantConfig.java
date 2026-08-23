package com.sc1hub.assistant.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        AssistantProperties.class,
        AssistantRagProperties.class,
        GeminiProperties.class,
        OpenAiProperties.class,
        AssistantBotProperties.class
})
public class AssistantConfig {

    @Bean
    public RestTemplate geminiRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Bean(name = "assistantOpenAiRestTemplate")
    public RestTemplate assistantOpenAiRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }
}
