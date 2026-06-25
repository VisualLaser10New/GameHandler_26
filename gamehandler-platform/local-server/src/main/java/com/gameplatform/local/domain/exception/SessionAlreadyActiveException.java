package com.gameplatform.local.domain.exception;

public class SessionAlreadyActiveException extends RuntimeException {
    public SessionAlreadyActiveException(String message) {
        super(message);
    }
}
