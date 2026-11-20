package com.sc1hub.buildtimer.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Getter
@EqualsAndHashCode
@ToString
public class BuildTimerBuildDTO {
    private final String id;
    private final String race;
    private final String matchup;
    private final String name;
    private final String summary;
    private final String sourceTitle;
    private final String sourceUrl;
    private final String note;
    private final List<BuildTimerStepDTO> steps;
    private final List<BuildTimerCheckpointDTO> checkpoints;

    @JsonCreator
    public BuildTimerBuildDTO(
            @JsonProperty("id") String id,
            @JsonProperty("race") String race,
            @JsonProperty("matchup") String matchup,
            @JsonProperty("name") String name,
            @JsonProperty("summary") String summary,
            @JsonProperty("sourceTitle") String sourceTitle,
            @JsonProperty("sourceUrl") String sourceUrl,
            @JsonProperty("note") String note,
            @JsonProperty("steps") List<BuildTimerStepDTO> steps,
            @JsonProperty("checkpoints") List<BuildTimerCheckpointDTO> checkpoints) {
        this.id = id;
        this.race = race;
        this.matchup = matchup;
        this.name = name;
        this.summary = summary;
        this.sourceTitle = sourceTitle;
        this.sourceUrl = sourceUrl;
        this.note = note;
        this.steps = steps == null ? List.of() : List.copyOf(steps);
        this.checkpoints = checkpoints == null ? List.of() : List.copyOf(checkpoints);
    }
}
