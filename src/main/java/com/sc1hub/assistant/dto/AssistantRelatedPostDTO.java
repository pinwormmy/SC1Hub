package com.sc1hub.assistant.dto;

import lombok.Data;

import java.util.Date;

@Data
public class AssistantRelatedPostDTO {
    private String boardTitle;
    private int postNum;
    private String title;
    private Date regDate;
    private String url;
}

