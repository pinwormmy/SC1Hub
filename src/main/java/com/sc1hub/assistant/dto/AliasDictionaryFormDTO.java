package com.sc1hub.assistant.dto;

import lombok.Data;

@Data
public class AliasDictionaryFormDTO {
    private Long id;
    private String alias;
    private String canonicalTerms;
    private String matchupHint;
    private String boostBoardIds;
}
