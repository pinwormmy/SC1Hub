package com.sc1hub.chat.dto;

import lombok.Data;

@Data
public class ChatSanctionRequestDTO {
    private String type;
    private String nickname;
    private Integer minutes;
    private String reason;
}
