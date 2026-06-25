package com.gameplatform.local.domain.exception;

public class InvalidGameStateTransitionException extends RuntimeException {
    public InvalidGameStateTransitionException(String message) {
        super(message);
    }
}
