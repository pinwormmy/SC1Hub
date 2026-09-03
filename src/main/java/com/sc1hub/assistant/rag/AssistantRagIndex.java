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
    /** 임베딩 예산/호출 실패로 일부 글이 빠진 채 저장된 인덱스. reindex를 다시 돌려야 완성된다. */
    private boolean incomplete;
    private List<AssistantRagChunk> chunks = new ArrayList<>();
    private List<AssistantRagBoardSnapshot> boardSnapshots = new ArrayList<>();
}
