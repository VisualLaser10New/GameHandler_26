package com.gameplatform.local.domain.exception;

/** 403 — reserved for future building-routing validation (Local trusts routing per ambiguity O). */
public class TournamentMatchBuildingMismatchException extends RuntimeException {
    public TournamentMatchBuildingMismatchException(String message) {
        super(message);
    }
}