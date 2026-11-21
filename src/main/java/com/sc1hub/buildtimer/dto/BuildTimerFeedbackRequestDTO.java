package com.sc1hub.buildtimer.dto;

import lombok.Data;

@Data
public class BuildTimerFeedbackRequestDTO {
    private String buildId;
    private String category;
    private String message;
    private String contact;
    /** 봇 차단용 허니팟 필드. 정상 사용자는 비워 둔다. */
    private String website;
}
