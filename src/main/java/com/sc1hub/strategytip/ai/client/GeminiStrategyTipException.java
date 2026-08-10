package com.sc1hub.strategytip.ai.client;

public class GeminiStrategyTipException extends RuntimeException {

    private final boolean usageAvailable;
    private final int inputTokens;
    private final int outputTokens;
    private final int searchQueryCount;

    public GeminiStrategyTipException(String message) {
        this(message, null, false, 0, 0, 0);
    }

    public GeminiStrategyTipException(String message, Throwable cause) {
        this(message, cause, false, 0, 0, 0);
    }

    public GeminiStrategyTipException(String message, Throwable cause,
                                      int inputTokens, int outputTokens,
                                      int searchQueryCount) {
        this(message, cause, true, inputTokens, outputTokens, searchQueryCount);
    }

    private GeminiStrategyTipException(String message, Throwable cause,
                                       boolean usageAvailable,
                                       int inputTokens, int outputTokens,
                                       int searchQueryCount) {
        super(message, cause);
        this.usageAvailable = usageAvailable;
        this.inputTokens = Math.max(0, inputTokens);
        this.outputTokens = Math.max(0, outputTokens);
        this.searchQueryCount = Math.max(0, searchQueryCount);
    }

    public boolean hasUsage() {
        return usageAvailable;
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
}
