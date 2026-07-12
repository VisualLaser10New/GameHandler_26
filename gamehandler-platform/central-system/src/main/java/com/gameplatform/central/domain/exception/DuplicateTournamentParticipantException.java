package com.gameplatform.central.domain.exception;

/**
 * Raised when an individual participant (userId) is already registered for a
 * tournament and a duplicate registration is attempted. Maps to HTTP 409.
 */
public class DuplicateTournamentParticipantException extends RuntimeException {
    public DuplicateTournamentParticipantException(String message) {
        super(message);
    }
}
