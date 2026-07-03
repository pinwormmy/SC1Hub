package com.sc1hub.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "sc1hub.chat")
public class ChatProperties {
    private boolean enabled = true;
    private int historySize = 50;
    private int bufferSize = 200;
    private int maxMessageLength = 300;
    private int aiMaxMessageLength = 1000;
    private long minIntervalMillis = 2000;
    private int pollIntervalMillis = 2500;
    private int hiddenPollIntervalMillis = 10000;
    private String aiNickname = "AI도우미";
    private List<String> blockedWords = new ArrayList<>();
}
