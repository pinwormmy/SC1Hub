package com.sc1hub.chat.dto;

import lombok.Data;

@Data
public class ChatSelfDTO {
    private String nickname;
    private String role;
    private boolean muted;
    private String mutedText;
    private int pollIntervalMillis;
    private int hiddenPollIntervalMillis;
    private int maxMessageLength;
}
