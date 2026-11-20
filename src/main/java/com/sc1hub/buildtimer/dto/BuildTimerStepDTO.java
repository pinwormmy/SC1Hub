package com.sc1hub.buildtimer.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode
@ToString
public class BuildTimerStepDTO {
    /** 원글에 적힌 인구수. 원글에 인구수가 없는 단계는 비어 있다. */
    private final String supply;
    private final String action;

    @JsonCreator
    public BuildTimerStepDTO(
            @JsonProperty("supply") String supply,
            @JsonProperty("action") String action) {
        this.supply = supply;
        this.action = action;
    }
}
