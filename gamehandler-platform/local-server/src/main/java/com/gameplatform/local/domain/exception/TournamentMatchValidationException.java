package com.gameplatform.local.domain.exception;

/** 400 — team_allowed mismatch or participant mismatch. */
public class TournamentMatchValidationException extends RuntimeException {
    public TournamentMatchValidationException(String message) {
        super(message);
    }
}