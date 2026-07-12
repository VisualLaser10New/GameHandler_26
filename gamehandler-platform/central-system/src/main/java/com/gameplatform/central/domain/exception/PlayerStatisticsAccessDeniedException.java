package com.gameplatform.central.domain.exception;

/**
 * Thrown when a caller requests another player's statistics
 * ({@code GET /api/players/{userId}/statistics}) while being neither the
 * player themselves nor a {@code PLATFORM_ADMIN} (FASE 3, PIANO &sect;2.4).
 *
 * <p>Mapped to HTTP 403 Forbidden by the Central {@code GlobalExceptionHandler}.</p>
 */
public class PlayerStatisticsAccessDeniedException extends RuntimeException {
    public PlayerStatisticsAccessDeniedException(String message) {
        super(message);
    }
}
