package com.sc1hub.assistant.service;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AssistantAnswerResult {
    private String answer;
    private List<String> usedPostIds = new ArrayList<>();
}

