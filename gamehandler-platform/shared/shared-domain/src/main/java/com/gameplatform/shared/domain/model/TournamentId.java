package com.gameplatform.shared.domain.model;

public record TournamentId(String value) {
    public TournamentId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TournamentId cannot be null");
        }
    }
}