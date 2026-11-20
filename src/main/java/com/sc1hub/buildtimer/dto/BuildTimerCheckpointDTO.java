package com.sc1hub.buildtimer.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode
@ToString
public class BuildTimerCheckpointDTO {
    /** 알림 기준 시각(초). 원글이 구간으로 적은 경우 구간의 시작이다. */
    private final int at;
    /** 원글 표기 그대로의 시각. 예: "3:15~3:25", "5분대" */
    private final String time;
    private final String text;

    @JsonCreator
    public BuildTimerCheckpointDTO(
            @JsonProperty("at") int at,
            @JsonProperty("time") String time,
            @JsonProperty("text") String text) {
        this.at = at;
        this.time = time;
        this.text = text;
    }
}
