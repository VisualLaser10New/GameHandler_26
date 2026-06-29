package com.gameplatform.central.domain.model;

import java.time.Instant;

public record FailedLoginAttempt(String username, Instant attemptTime) {
    public FailedLoginAttempt {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username cannot be null or blank");
        }
        if (attemptTime == null) {
            throw new IllegalArgumentException("attemptTime cannot be null");
        }
    }
}
