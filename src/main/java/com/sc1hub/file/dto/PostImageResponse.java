package com.sc1hub.file.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PostImageResponse {

    private String fileName;
    private String url;
    private String mimeType;
    private int width;
    private int height;
    private long bytes;
}
