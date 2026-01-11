package com.sc1hub.assistant.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AssistantSearchLogDTO {
    private Long id;
    private String message;
    private LocalDateTime createdAt;
}
