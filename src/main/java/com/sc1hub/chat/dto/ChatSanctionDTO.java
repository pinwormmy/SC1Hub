package com.sc1hub.chat.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
public class ChatSanctionDTO {
    private long id;
    private String sanctionType;
    private String nickname;
    private String reason;
    private String createdBy;

    @JsonIgnore
    private String memberId;
    @JsonIgnore
    private String ip;
    @JsonIgnore
    private LocalDateTime expiresAt;
    @JsonIgnore
    private LocalDateTime regDate;

    public String getExpiresAtText() {
        if (expiresAt == null) {
            return "영구";
        }
        return expiresAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
}
