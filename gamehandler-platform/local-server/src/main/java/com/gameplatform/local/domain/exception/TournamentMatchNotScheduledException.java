package com.gameplatform.local.domain.exception;

/** 409 — match not in SCHEDULED status when a start was attempted. */
public class TournamentMatchNotScheduledException extends RuntimeException {
    public TournamentMatchNotScheduledException(String message) {
        super(message);
    }
}