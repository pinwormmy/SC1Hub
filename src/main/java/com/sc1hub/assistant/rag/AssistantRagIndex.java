package com.sc1hub.assistant.rag;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class AssistantRagIndex {
    private int version = 1;
    private String embeddingModel;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;
    private int dimension;
    private List<AssistantRagChunk> chunks = new ArrayList<>();
}
