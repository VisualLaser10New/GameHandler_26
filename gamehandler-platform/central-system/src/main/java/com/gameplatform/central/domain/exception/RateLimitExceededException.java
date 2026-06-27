package com.gameplatform.central.domain.exception;

/**
 * Thrown when a client has exceeded the allowed number of requests within a time window.
 * Maps to HTTP 429 Too Many Requests.
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }

    public RateLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
