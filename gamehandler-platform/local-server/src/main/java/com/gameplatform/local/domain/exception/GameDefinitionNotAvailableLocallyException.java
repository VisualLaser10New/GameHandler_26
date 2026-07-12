package com.gameplatform.local.domain.exception;

public class GameDefinitionNotAvailableLocallyException extends RuntimeException {
    public GameDefinitionNotAvailableLocallyException(String message) {
        super(message);
    }
}