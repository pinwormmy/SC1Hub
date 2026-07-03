package com.sc1hub.chat.dto;

import lombok.Data;

@Data
public class ChatPostResponseDTO {
    private ChatMessageDTO message;
    private long lastSeq;
    private String error;
}
