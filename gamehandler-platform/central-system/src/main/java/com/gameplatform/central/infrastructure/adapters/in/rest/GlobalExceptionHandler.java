package com.gameplatform.central.infrastructure.adapters.in.rest;

import com.gameplatform.central.domain.exception.DuplicateTournamentParticipantException;
import com.gameplatform.central.domain.exception.GameDefinitionNotFoundException;
import com.gameplatform.central.domain.exception.InvalidCredentialsException;
import com.gameplatform.central.domain.exception.InvalidGameDefinitionException;
import com.gameplatform.central.domain.exception.InvalidTournamentException;
import com.gameplatform.central.domain.exception.InvalidTournamentStateException;
import com.gameplatform.central.domain.exception.PlayerStatisticsAccessDeniedException;
import com.gameplatform.central.domain.exception.RateLimitExceededException;
import com.gameplatform.central.domain.exception.TournamentNotFoundException;
import com.gameplatform.central.domain.exception.TournamentRegistrationClosedException;
import com.gameplatform.central.domain.exception.UserAlreadyExistsException;
import com.gameplatform.central.domain.exception.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralised exception-to-HTTP-status mapping for all REST controllers.
 *
 * <p>Follows the Separation of Concerns principle: controllers stay free of
 * try-catch boilerplate while every domain exception is mapped to a well-defined
 * HTTP response exactly once.</p>
 *
 * <ul>
 *   <li>{@link UserNotFoundException}       → 404 Not Found</li>
 *   <li>{@link UserAlreadyExistsException}  → 409 Conflict</li>
 *   <li>{@link InvalidCredentialsException} → 401 Unauthorized</li>
 *   <li>{@link RateLimitExceededException}  → 429 Too Many Requests</li>
 *   <li>{@link MethodArgumentNotValidException} → 400 Bad Request (Bean Validation)</li>
 *   <li>{@link IllegalArgumentException}    → 400 Bad Request</li>
 *   <li>{@link InvalidGameDefinitionException}  → 400 Bad Request</li>
 *   <li>{@link GameDefinitionNotFoundException} → 404 Not Found</li>
 *   <li>{@link PlayerStatisticsAccessDeniedException} → 403 Forbidden</li>
 *   <li>{@link InvalidTournamentException} → 400 Bad Request</li>
 *   <li>{@link TournamentNotFoundException} → 404 Not Found</li>
 *   <li>{@link InvalidTournamentStateException} → 400 Bad Request</li>
 *   <li>{@link TournamentRegistrationClosedException} → 409 Conflict</li>
 *   <li>{@link DuplicateTournamentParticipantException} → 409 Conflict</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ──────────────────────────────────────────────────────────────────────────
    // 400 Bad Request
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Handles constraint violations produced by {@code @Valid} on request bodies.
     * Returns one combined message listing all failing fields.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.debug("Validation failure: {}", details);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", details));
    }

    /**
     * Handles {@link IllegalArgumentException} thrown when e.g. an invalid
     * enum value is supplied as a request parameter.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        log.debug("Illegal argument: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * Handles {@link InvalidGameDefinitionException} thrown when an upsert
     * request carries an invalid (null) game definition.
     */
    @ExceptionHandler(InvalidGameDefinitionException.class)
    public ResponseEntity<Map<String, String>> handleInvalidGameDefinition(InvalidGameDefinitionException ex) {
        log.warn("Invalid game definition: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * Handles {@link InvalidTournamentException} thrown when a tournament
     * upsert request carries invalid data (FASE 4).
     */
    @ExceptionHandler(InvalidTournamentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidTournament(InvalidTournamentException ex) {
        log.warn("Invalid tournament: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * Handles {@link InvalidTournamentStateException} thrown when a tournament
     * operation is attempted while in an incompatible state (FASE 4).
     */
    @ExceptionHandler(InvalidTournamentStateException.class)
    public ResponseEntity<Map<String, String>> handleInvalidTournamentState(InvalidTournamentStateException ex) {
        log.warn("Invalid tournament state: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 401 Unauthorized
    // ──────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleInvalidCredentials(InvalidCredentialsException ex) {
        log.debug("Authentication failure: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", ex.getMessage()));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 404 Not Found
    // ──────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUserNotFound(UserNotFoundException ex) {
        log.debug("User not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * Handles {@link GameDefinitionNotFoundException} thrown when a requested
     * game definition does not exist in the central Source-of-Truth.
     */
    @ExceptionHandler(GameDefinitionNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleGameDefinitionNotFound(GameDefinitionNotFoundException ex) {
        log.warn("Game definition not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * Handles {@link TournamentNotFoundException} thrown when a requested
     * tournament does not exist in the central Source-of-Truth (FASE 4).
     */
    @ExceptionHandler(TournamentNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleTournamentNotFound(TournamentNotFoundException ex) {
        log.warn("Tournament not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 403 Forbidden
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Handles {@link PlayerStatisticsAccessDeniedException} thrown when a
     * caller requests another player's statistics without being a
     * {@code PLATFORM_ADMIN} or the player themselves (FASE 3).
     */
    @ExceptionHandler(PlayerStatisticsAccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handlePlayerStatisticsAccessDenied(PlayerStatisticsAccessDeniedException ex) {
        log.warn("Player statistics access denied: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", ex.getMessage()));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 409 Conflict
    // ──────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        log.debug("User already exists: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * Handles {@link TournamentRegistrationClosedException} thrown when a
     * registration is attempted on a tournament that is no longer open (FASE 4).
     */
    @ExceptionHandler(TournamentRegistrationClosedException.class)
    public ResponseEntity<Map<String, String>> handleTournamentRegistrationClosed(TournamentRegistrationClosedException ex) {
        log.warn("Tournament registration closed: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * Handles {@link DuplicateTournamentParticipantException} thrown when a
     * player attempts to register for a tournament more than once (FASE 4).
     */
    @ExceptionHandler(DuplicateTournamentParticipantException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateTournamentParticipant(DuplicateTournamentParticipantException ex) {
        log.warn("Duplicate tournament participant: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 429 Too Many Requests
    // ──────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, String>> handleRateLimitExceeded(RateLimitExceededException ex) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", ex.getMessage()));
    }
}
