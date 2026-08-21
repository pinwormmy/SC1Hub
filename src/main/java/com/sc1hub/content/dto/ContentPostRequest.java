package com.sc1hub.content.dto;

import lombok.Data;

@Data
public class ContentPostRequest {

    private String title;
    private String content;
    private String writer;
    private boolean notice;
}
