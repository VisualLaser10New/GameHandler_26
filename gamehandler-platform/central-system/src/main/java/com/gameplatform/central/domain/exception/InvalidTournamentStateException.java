package com.gameplatform.central.domain.exception;

/**
 * Raised by {@link com.gameplatform.central.domain.model.Tournament} transition
 * methods ({@code openRegistration}/{@code cancel}/{@code startProgress}/{@code
 * complete}) when the current
 * {@link com.gameplatform.shared.domain.model.TournamentStatus} does not admit
 * the requested transition per PIANO FASE 4 &sect;3.1 (C.6). Maps to HTTP 400.
 */
public class InvalidTournamentStateException extends RuntimeException {
    public InvalidTournamentStateException(String message) {
        super(message);
    }
}
