package com.gameplatform.local.domain.exception;

public class ConcurrentStateException extends RuntimeException {
    public ConcurrentStateException(String message) {
        super(message);
    }

    public ConcurrentStateException(String message, Throwable cause) {
        super(message, cause);
    }
}