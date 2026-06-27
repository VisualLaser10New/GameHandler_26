package com.gameplatform.central.domain.exception;

public class StatisticsNotFoundException extends RuntimeException {
    public StatisticsNotFoundException(String message) {
        super(message);
    }

    public StatisticsNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
