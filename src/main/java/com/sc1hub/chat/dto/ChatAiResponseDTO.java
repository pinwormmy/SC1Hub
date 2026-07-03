package com.sc1hub.chat.dto;

import lombok.Data;

@Data
public class ChatAiResponseDTO {
    private ChatMessageDTO questionMessage;
    private ChatMessageDTO answerMessage;
    private String usageText;
    private long lastSeq;
    private String error;
}
