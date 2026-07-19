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
     * Gestisce le violazioni di vincoli di validazione prodotte da {@code @Valid} sui corpi delle richieste.
     *
     * <p>Restituisce uno stato {@code 400 Bad Request} con un messaggio combinato che elenca
     * tutti i campi non validi e la relativa motivazione.</p>
     *
     * @param ex eccezione contenente il risultato del binding e gli errori di campo, non {@code null}
     * @return {@link ResponseEntity} con stato {@code 400 Bad Request} e una mappa con la chiave
     *         {@code error} contenente il dettaglio degli errori di validazione
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
     * Gestisce {@link IllegalArgumentException} lanciata, ad esempio, quando viene fornito
     * un valore non valido (come un enum) come parametro di richiesta.
     *
     * @param ex eccezione contenente il messaggio descrittivo dell'errore, non {@code null}
     * @return {@link ResponseEntity} con stato {@code 400 Bad Request} e una mappa con la chiave
     *         {@code error} contenente il messaggio dell'eccezione
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        log.debug("Illegal argument: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * Gestisce {@link InvalidGameDefinitionException} lanciata quando una richiesta di upsert
     * trasporta una definizione di gioco non valida.
     *
     * @param ex eccezione contenente il messaggio descrittivo dell'errore, non {@code null}
     * @return {@link ResponseEntity} con stato {@code 400 Bad Request} e una mappa con la chiave
     *         {@code error} contenente il messaggio dell'eccezione
     */
    @ExceptionHandler(InvalidGameDefinitionException.class)
    public ResponseEntity<Map<String, String>> handleInvalidGameDefinition(InvalidGameDefinitionException ex) {
        log.warn("Invalid game definition: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * Gestisce {@link InvalidTournamentException} lanciata quando una richiesta di upsert
     * di un torneo trasporta dati non validi.
     *
     * @param ex eccezione contenente il messaggio descrittivo dell'errore, non {@code null}
     * @return {@link ResponseEntity} con stato {@code 400 Bad Request} e una mappa con la chiave
     *         {@code error} contenente il messaggio dell'eccezione
     */
    @ExceptionHandler(InvalidTournamentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidTournament(InvalidTournamentException ex) {
        log.warn("Invalid tournament: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * Gestisce {@link InvalidTournamentStateException} lanciata quando un'operazione su un torneo
     * viene tentata in uno stato incompatibile.
     *
     * @param ex eccezione contenente il messaggio descrittivo dell'errore, non {@code null}
     * @return {@link ResponseEntity} con stato {@code 400 Bad Request} e una mappa con la chiave
     *         {@code error} contenente il messaggio dell'eccezione
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

    /**
     * Gestisce {@link InvalidCredentialsException} lanciata in caso di credenziali non valide.
     *
     * @param ex eccezione contenente il messaggio descrittivo dell'errore, non {@code null}
     * @return {@link ResponseEntity} con stato {@code 401 Unauthorized} e una mappa con la chiave
     *         {@code error} contenente il messaggio dell'eccezione
     */
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

    /**
     * Gestisce {@link UserNotFoundException} lanciata quando l'utente richiesto non esiste.
     *
     * @param ex eccezione contenente il messaggio descrittivo dell'errore, non {@code null}
     * @return {@link ResponseEntity} con stato {@code 404 Not Found} e una mappa con la chiave
     *         {@code error} contenente il messaggio dell'eccezione
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUserNotFound(UserNotFoundException ex) {
        log.debug("User not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * Gestisce {@link GameDefinitionNotFoundException} lanciata quando una definizione di gioco
     * richiesta non esiste nel sistema centrale di riferimento.
     *
     * @param ex eccezione contenente il messaggio descrittivo dell'errore, non {@code null}
     * @return {@link ResponseEntity} con stato {@code 404 Not Found} e una mappa con la chiave
     *         {@code error} contenente il messaggio dell'eccezione
     */
    @ExceptionHandler(GameDefinitionNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleGameDefinitionNotFound(GameDefinitionNotFoundException ex) {
        log.warn("Game definition not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * Gestisce {@link TournamentNotFoundException} lanciata quando un torneo richiesto
     * non esiste nel sistema centrale di riferimento.
     *
     * @param ex eccezione contenente il messaggio descrittivo dell'errore, non {@code null}
     * @return {@link ResponseEntity} con stato {@code 404 Not Found} e una mappa con la chiave
     *         {@code error} contenente il messaggio dell'eccezione
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
     * Gestisce {@link PlayerStatisticsAccessDeniedException} lanciata quando un chiamante
     * richiede le statistiche di un altro giocatore senza disporre dei permessi necessari.
     *
     * @param ex eccezione contenente il messaggio descrittivo dell'errore, non {@code null}
     * @return {@link ResponseEntity} con stato {@code 403 Forbidden} e una mappa con la chiave
     *         {@code error} contenente il messaggio dell'eccezione
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

    /**
     * Gestisce {@link UserAlreadyExistsException} lanciata quando si tenta di registrare
     * un utente già esistente.
     *
     * @param ex eccezione contenente il messaggio descrittivo dell'errore, non {@code null}
     * @return {@link ResponseEntity} con stato {@code 409 Conflict} e una mappa con la chiave
     *         {@code error} contenente il messaggio dell'eccezione
     */
    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        log.debug("User already exists: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * Gestisce {@link TournamentRegistrationClosedException} lanciata quando si tenta di
     * registrarsi a un torneo la cui iscrizione non è più aperta.
     *
     * @param ex eccezione contenente il messaggio descrittivo dell'errore, non {@code null}
     * @return {@link ResponseEntity} con stato {@code 409 Conflict} e una mappa con la chiave
     *         {@code error} contenente il messaggio dell'eccezione
     */
    @ExceptionHandler(TournamentRegistrationClosedException.class)
    public ResponseEntity<Map<String, String>> handleTournamentRegistrationClosed(TournamentRegistrationClosedException ex) {
        log.warn("Tournament registration closed: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * Gestisce {@link DuplicateTournamentParticipantException} lanciata quando un giocatore
     * tenta di registrarsi più di una volta allo stesso torneo.
     *
     * @param ex eccezione contenente il messaggio descrittivo dell'errore, non {@code null}
     * @return {@link ResponseEntity} con stato {@code 409 Conflict} e una mappa con la chiave
     *         {@code error} contenente il messaggio dell'eccezione
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

    /**
     * Gestisce {@link RateLimitExceededException} lanciata quando è stato superato il limite
     * di richieste consentito per un determinato client.
     *
     * @param ex eccezione contenente il messaggio descrittivo dell'errore, non {@code null}
     * @return {@link ResponseEntity} con stato {@code 429 Too Many Requests} e una mappa con la
     *         chiave {@code error} contenente il messaggio dell'eccezione
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, String>> handleRateLimitExceeded(RateLimitExceededException ex) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", ex.getMessage()));
    }
}
