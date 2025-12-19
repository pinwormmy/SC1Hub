package com.sc1hub.assistant.rag;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class AssistantRagBoardSnapshot {
    private String boardTitle;
    private int maxPostNum;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date maxRegDate;
    private int postCount;
}
