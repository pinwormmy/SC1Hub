package com.sc1hub.strategytip.ai.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StrategyTipAiGeneratedBatch {

    private final List<Draft> drafts;
    private final String model;
    private final int inputTokens;
    private final int outputTokens;

    public StrategyTipAiGeneratedBatch(List<Draft> drafts, String model,
                                       int inputTokens, int outputTokens) {
        this.drafts = Collections.unmodifiableList(new ArrayList<>(
                drafts == null ? Collections.emptyList() : drafts));
        this.model = model;
        this.inputTokens = Math.max(0, inputTokens);
        this.outputTokens = Math.max(0, outputTokens);
    }

    public List<Draft> getDrafts() {
        return drafts;
    }

    public String getModel() {
        return model;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public static final class Draft {

        private final String category;
        private final String content;

        public Draft(String category, String content) {
            this.category = category;
            this.content = content;
        }

        public String getCategory() {
            return category;
        }

        public String getContent() {
            return content;
        }

    }
}
