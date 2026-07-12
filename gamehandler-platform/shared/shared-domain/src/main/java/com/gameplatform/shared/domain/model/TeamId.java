package com.gameplatform.shared.domain.model;

public record TeamId(String value) {
    public TeamId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TeamId cannot be null");
        }
    }
}