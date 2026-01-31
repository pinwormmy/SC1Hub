package com.sc1hub.assistant.dto;

import lombok.Data;

import java.util.Date;

@Data
public class AliasDictionaryDTO {
    private long id;
    private String alias;
    private String canonicalTerms;
    private String matchupHint;
    private String boostBoardIds;
    private Date createdAt;
    private Date updatedAt;
}
