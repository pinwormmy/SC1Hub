package com.sc1hub.buildtimer.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BuildTimerFeedbackDTO {
    private Long id;
    private String buildId;
    private String buildLabel;
    private String category;
    private String message;
    private String contact;
    private String memberId;
    private String nickname;
    private String ip;
    private String userAgent;
    private String status;
    private LocalDateTime regDate;
}
