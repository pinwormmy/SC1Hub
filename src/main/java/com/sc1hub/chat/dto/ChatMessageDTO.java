package com.sc1hub.chat.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessageDTO {
    private long id;
    private String nickname;
    private String role;
    private String content;
    private String regDate;

    @JsonIgnore
    private String memberId;
    @JsonIgnore
    private String ip;
    @JsonIgnore
    private boolean deleted;
    @JsonIgnore
    private LocalDateTime createdAt;
}
