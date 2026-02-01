package com.sc1hub.assistant.search;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class AssistantQueryParseResult {
    private String intent;
    @JsonProperty("player_race")
    private String playerRace;
    @JsonProperty("opponent_race")
    private String opponentRace;
    private String matchup;
    private double confidence;
    private List<String> keywords = new ArrayList<>();
    @JsonProperty("expanded_terms")
    private List<String> expandedTerms = new ArrayList<>();
    @JsonProperty("board_weights")
    private Map<String, Double> boardWeights = new LinkedHashMap<>();
    @JsonProperty("alias_matched")
    private boolean aliasMatched;
}
