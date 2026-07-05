package com.gameplatform.central.infrastructure.adapters.out.rest;

/** Marker exception for transient HTTP failures that should be retried. */
public class TransientPushException extends RuntimeException {
    public TransientPushException(String message, Throwable cause) {
        super(message, cause);
    }
}
