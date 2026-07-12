package com.gameplatform.shared.domain.model;

public record TournamentMatchId(String value) {
    public TournamentMatchId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TournamentMatchId cannot be null");
        }
    }
}