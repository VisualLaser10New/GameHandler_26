package com.gameplatform.central.domain.exception;

public class GameDefinitionNotFoundException extends RuntimeException {
    public GameDefinitionNotFoundException(String message) {
        super(message);
    }
}
