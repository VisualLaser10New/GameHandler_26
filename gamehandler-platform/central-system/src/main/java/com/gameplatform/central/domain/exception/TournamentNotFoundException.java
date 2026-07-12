package com.gameplatform.central.domain.exception;

/**
 * Raised when a tournament identified by its
 * {@link com.gameplatform.shared.domain.model.TournamentId} cannot be resolved
 * (GET /{id}, or registration/unregister against an absent tournament). Maps
 * to HTTP 404.
 */
public class TournamentNotFoundException extends RuntimeException {
    public TournamentNotFoundException(String message) {
        super(message);
    }
}
