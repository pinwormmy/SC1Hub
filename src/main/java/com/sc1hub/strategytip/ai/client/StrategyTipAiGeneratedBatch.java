package com.sc1hub.strategytip.ai.client;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StrategyTipAiGeneratedBatch {

    private final List<Draft> drafts;
    private final String model;
    private final int inputTokens;
    private final int outputTokens;
    private final int searchQueryCount;
    private final Map<String, String> citationTitlesByUrl;

    public StrategyTipAiGeneratedBatch(List<Draft> drafts, String model,
                                       int inputTokens, int outputTokens) {
        this(drafts, model, inputTokens, outputTokens, 0, Collections.emptyMap());
    }

    public StrategyTipAiGeneratedBatch(List<Draft> drafts, String model,
                                       int inputTokens, int outputTokens,
                                       int searchQueryCount) {
        this(drafts, model, inputTokens, outputTokens, searchQueryCount, Collections.emptyMap());
    }

    public StrategyTipAiGeneratedBatch(List<Draft> drafts, String model,
                                       int inputTokens, int outputTokens,
                                       int searchQueryCount,
                                       Map<String, String> citationTitlesByUrl) {
        this.drafts = Collections.unmodifiableList(new ArrayList<>(
                drafts == null ? Collections.emptyList() : drafts));
        this.model = model;
        this.inputTokens = Math.max(0, inputTokens);
        this.outputTokens = Math.max(0, outputTokens);
        this.searchQueryCount = Math.max(0, searchQueryCount);

        Map<String, String> citations = new LinkedHashMap<>();
        if (citationTitlesByUrl != null) {
            for (Map.Entry<String, String> entry : citationTitlesByUrl.entrySet()) {
                String url = normalize(entry.getKey());
                if (!StringUtils.hasText(url)) {
                    continue;
                }
                String title = normalize(entry.getValue());
                citations.putIfAbsent(url, title);
            }
        }
        this.citationTitlesByUrl = Collections.unmodifiableMap(citations);
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

    public int getSearchQueryCount() {
        return searchQueryCount;
    }

    public boolean hasCitation(String url) {
        return citationTitlesByUrl.containsKey(normalize(url));
    }

    public String citationTitle(String url) {
        return citationTitlesByUrl.get(normalize(url));
    }

    public Map<String, String> getCitationTitlesByUrl() {
        return citationTitlesByUrl;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Draft {

        private final String category;
        private final String content;
        private final String sourceId;
        private final String evidenceSummary;
        private final String externalSourceUrl;
        private final String externalSourceTitle;
        private final String externalEvidenceSummary;

        public Draft(String category, String content, String sourceId, String evidenceSummary) {
            this(category, content, sourceId, evidenceSummary, null, null, null);
        }

        public Draft(String category, String content, String sourceId, String evidenceSummary,
                     String externalSourceUrl, String externalSourceTitle,
                     String externalEvidenceSummary) {
            this.category = category;
            this.content = content;
            this.sourceId = sourceId;
            this.evidenceSummary = evidenceSummary;
            this.externalSourceUrl = externalSourceUrl;
            this.externalSourceTitle = externalSourceTitle;
            this.externalEvidenceSummary = externalEvidenceSummary;
        }

        public String getCategory() {
            return category;
        }

        public String getContent() {
            return content;
        }

        public String getSourceId() {
            return sourceId;
        }

        public String getEvidenceSummary() {
            return evidenceSummary;
        }

        public String getExternalSourceUrl() {
            return externalSourceUrl;
        }

        public String getExternalSourceTitle() {
            return externalSourceTitle;
        }

        public String getExternalEvidenceSummary() {
            return externalEvidenceSummary;
        }
    }
}
