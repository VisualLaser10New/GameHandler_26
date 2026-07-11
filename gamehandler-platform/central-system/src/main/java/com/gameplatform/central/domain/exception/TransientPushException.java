package com.gameplatform.central.domain.exception;

/** Marker exception for transient HTTP failures that should be retried. */
public class TransientPushException extends RuntimeException {
    public TransientPushException(String message, Throwable cause) {
        super(message, cause);
    }
}
