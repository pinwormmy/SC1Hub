package com.sc1hub.chat.service;

import org.springframework.http.HttpStatus;

public class ChatRejectedException extends RuntimeException {

    private final HttpStatus status;

    public ChatRejectedException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
