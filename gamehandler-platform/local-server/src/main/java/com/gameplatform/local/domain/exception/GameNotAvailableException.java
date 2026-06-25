package com.gameplatform.local.domain.exception;

public class GameNotAvailableException extends RuntimeException {
    public GameNotAvailableException(String message) {
        super(message);
    }
}
