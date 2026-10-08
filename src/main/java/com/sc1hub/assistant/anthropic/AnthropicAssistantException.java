package com.sc1hub.assistant.anthropic;

public class AnthropicAssistantException extends RuntimeException {

    public AnthropicAssistantException(String message) {
        super(message);
    }

    public AnthropicAssistantException(String message, Throwable cause) {
        super(message, cause);
    }
}
