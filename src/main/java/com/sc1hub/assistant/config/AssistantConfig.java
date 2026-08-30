package com.sc1hub.assistant.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

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
    public RestTemplate assistantRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(30000);
        return new RestTemplate(requestFactory);
    }
}
