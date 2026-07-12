package com.gameplatform.local.domain.exception;

/** 404 — match id not present in tournament_matches_local. */
public class TournamentMatchNotFoundException extends RuntimeException {
    public TournamentMatchNotFoundException(String message) {
        super(message);
    }
}
