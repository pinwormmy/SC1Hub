package com.sc1hub.content.dto;

import lombok.Data;

@Data
public class ContentPostForm {

    private String title;
    private String content;
    private String writer;
    private boolean notice;
    private String imageAlt;
    private String imageCaption;
    private String youtubeUrl;
    private String youtubeTitle;
}
