package com.gameplatform.local.domain.model;

/**
 * Lifecycle status of a Local admin-request row
 * ({@code admin_requests_local.status}, PIANO §7.B). Mirror of the
 * Central {@code SyncEventProcessor} request-flow contract: a request
 * starts {@link #PENDING} when the W use case writes the outbox row,
 * transitions to {@link #COMPLETED} when the Central return-event
 * arrives carrying the {@code originatingRequestId} (the
 * {@code *SyncService} calls {@code markCompleted}), or transitions to
 * {@link #FAILED} when the {@code AdminRequestTimeoutService} times the
 * row out (no return-event observed within {@code admin.request.timeout-ms}).
 */
public enum AdminRequestStatus {
    PENDING,
    COMPLETED,
    FAILED;

    /**
     * Converte una stringa nel corrispondente valore enum, ignorando le maiuscole/minuscole.
     *
     * @param value la stringa da convertire (può essere null)
     * @return l'enum corrispondente, o null se la stringa è null o non riconosciuta
     */
    public static AdminRequestStatus fromString(String value) {
        if (value == null) {
            return null;
        }
        for (AdminRequestStatus status : values()) {
            if (status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        return null;
    }
}
