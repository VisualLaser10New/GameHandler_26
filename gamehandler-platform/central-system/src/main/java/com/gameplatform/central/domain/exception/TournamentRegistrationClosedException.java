package com.gameplatform.central.domain.exception;

/**
 * Raised when a register/unregister command is issued against a tournament
 * whose {@link com.gameplatform.shared.domain.model.TournamentStatus} is not
 * {@code OPEN_REGISTRATION}. Maps to HTTP 409.
 */
public class TournamentRegistrationClosedException extends RuntimeException {
    public TournamentRegistrationClosedException(String message) {
        super(message);
    }
}
