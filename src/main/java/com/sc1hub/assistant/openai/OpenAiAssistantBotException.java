package com.sc1hub.assistant.openai;

public class OpenAiAssistantBotException extends RuntimeException {

    public OpenAiAssistantBotException(String message) {
        super(message);
    }

    public OpenAiAssistantBotException(String message, Throwable cause) {
        super(message, cause);
    }
}
