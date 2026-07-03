package com.sc1hub.chat.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ChatPollResponseDTO {
    private List<ChatMessageDTO> messages = new ArrayList<>();
    private List<Long> deletedIds = new ArrayList<>();
    private long lastSeq;
    private ChatSelfDTO self;
    private String error;
}
