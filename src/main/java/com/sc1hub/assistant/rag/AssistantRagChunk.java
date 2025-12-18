package com.sc1hub.assistant.rag;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class AssistantRagChunk {
    private String id;
    private String boardTitle;
    private int postNum;
    private String title;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date regDate;
    private String url;
    private int chunkIndex;
    private String text;
    private float[] vector;
}

