package com.sc1hub.strategytip.ai.client;

public class StrategyTipAiClientException extends RuntimeException {

    private final boolean usageAvailable;
    private final int inputTokens;
    private final int outputTokens;

    public StrategyTipAiClientException(String message) {
        this(message, null, false, 0, 0);
    }

    public StrategyTipAiClientException(String message, Throwable cause) {
        this(message, cause, false, 0, 0);
    }

    public StrategyTipAiClientException(String message, Throwable cause,
                                        int inputTokens, int outputTokens) {
        this(message, cause, true, inputTokens, outputTokens);
    }

    private StrategyTipAiClientException(String message, Throwable cause,
                                         boolean usageAvailable,
                                         int inputTokens, int outputTokens) {
        super(message, cause);
        this.usageAvailable = usageAvailable;
        this.inputTokens = Math.max(0, inputTokens);
        this.outputTokens = Math.max(0, outputTokens);
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
}
