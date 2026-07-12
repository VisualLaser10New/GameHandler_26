package com.gameplatform.central.domain.exception;

/**
 * Raised by the tournament/registration services when an inbound command
 * violates a creation-time or registration invariant: {@code teamBased}/team
 * policy mismatch, fewer than two {@code buildingId}s, {@code teamSize}
 * incoherence, unknown {@code gameType}, team request on an individual
 * tournament, captain absent from members, member-count mismatch, or
 * unresolved principal. Maps to HTTP 400.
 */
public class InvalidTournamentException extends RuntimeException {
    public InvalidTournamentException(String message) {
        super(message);
    }
}
