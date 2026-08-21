package com.sc1hub.content.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ContentPostResponse {

    private int postNum;
    private String boardTitle;
    private String url;
}
