package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.domain.exception.BuildingNotRegisteredToAdminException;
import com.gameplatform.local.domain.exception.ConcurrentStateException;
import com.gameplatform.local.domain.exception.GameDefinitionNotAvailableLocallyException;
import com.gameplatform.local.domain.exception.TournamentMatchBuildingMismatchException;
import com.gameplatform.local.domain.exception.TournamentMatchNotFoundException;
import com.gameplatform.local.domain.exception.TournamentMatchNotScheduledException;
import com.gameplatform.local.domain.exception.TournamentMatchValidationException;
import com.gameplatform.local.domain.exception.UserAlreadyExistsException;
import com.gameplatform.local.domain.exception.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Gestore globale delle eccezioni per i controller REST. Mappa ogni
 * eccezione di dominio o di sicurezza al corrispondente status HTTP,
 * loggando l'errore con il messaggio appropriato.
 *
 * @see org.springframework.web.bind.annotation.RestControllerAdvice
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Gestisce le eccezioni di argomento non valido.
     *
     * @param ex l'eccezione catturata
     * @return una {@link ResponseEntity} con status 400
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Void> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.badRequest().build();
    }

    /**
     * Gestisce le eccezioni di utente non trovato.
     *
     * @param ex l'eccezione catturata
     * @return una {@link ResponseEntity} con status 401
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Void> handleUserNotFound(UserNotFoundException ex) {
        log.warn("User not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    /**
     * Gestisce le eccezioni di credenziali non valide.
     *
     * @param ex l'eccezione catturata
     * @return una {@link ResponseEntity} con status 401
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Void> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Invalid credentials: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    /**
     * Gestisce le eccezioni di utente già esistente.
     *
     * @param ex l'eccezione catturata
     * @return una {@link ResponseEntity} con status 409
     */
    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<Void> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        log.warn("Conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    /**
     * Gestisce le eccezioni di accesso negato.
     *
     * @param ex l'eccezione catturata
     * @return una {@link ResponseEntity} con status 403
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Void> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    /**
     * Gestisce le eccezioni di stato concorrente (lock ottimistico).
     *
     * @param ex l'eccezione catturata
     * @return una {@link ResponseEntity} con status 409
     */
    @ExceptionHandler(ConcurrentStateException.class)
    public ResponseEntity<Void> handleConcurrentState(ConcurrentStateException ex) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    /**
     * Gestisce le eccezioni di edificio non registrato per l'amministratore.
     *
     * @param ex l'eccezione catturata
     * @return una {@link ResponseEntity} con status 403
     */
    @ExceptionHandler(BuildingNotRegisteredToAdminException.class)
    public ResponseEntity<Void> handleBuildingNotRegistered(BuildingNotRegisteredToAdminException ex) {
        log.warn("Building not registered to admin: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    /**
     * Gestisce le eccezioni di definizione di gioco non disponibile localmente.
     *
     * @param ex l'eccezione catturata
     * @return una {@link ResponseEntity} con status 400
     */
    @ExceptionHandler(GameDefinitionNotAvailableLocallyException.class)
    public ResponseEntity<Void> handleGameDefinitionNotAvailableLocally(GameDefinitionNotAvailableLocallyException ex) {
        log.warn("Game definition not available locally: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }

    /**
     * Gestisce le eccezioni di partita torneo non trovata.
     *
     * @param ex l'eccezione catturata
     * @return una {@link ResponseEntity} con status 404
     */
    @ExceptionHandler(TournamentMatchNotFoundException.class)
    public ResponseEntity<Void> handleTournamentMatchNotFound(TournamentMatchNotFoundException ex) {
        log.warn("Tournament match not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /**
     * Gestisce le eccezioni di partita torneo non programmata.
     *
     * @param ex l'eccezione catturata
     * @return una {@link ResponseEntity} con status 409
     */
    @ExceptionHandler(TournamentMatchNotScheduledException.class)
    public ResponseEntity<Void> handleTournamentMatchNotScheduled(TournamentMatchNotScheduledException ex) {
        log.warn("Tournament match not scheduled: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    /**
     * Gestisce le eccezioni di disallineamento edificio per partita torneo.
     *
     * @param ex l'eccezione catturata
     * @return una {@link ResponseEntity} con status 403
     */
    @ExceptionHandler(TournamentMatchBuildingMismatchException.class)
    public ResponseEntity<Void> handleTournamentMatchBuildingMismatch(TournamentMatchBuildingMismatchException ex) {
        log.warn("Tournament match building mismatch: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    /**
     * Gestisce le eccezioni di validazione per partita torneo.
     *
     * @param ex l'eccezione catturata
     * @return una {@link ResponseEntity} con status 400
     */
    @ExceptionHandler(TournamentMatchValidationException.class)
    public ResponseEntity<Void> handleTournamentMatchValidation(TournamentMatchValidationException ex) {
        log.warn("Tournament match validation error: {}", ex.getMessage());
        return ResponseEntity.badRequest().build();
    }

    /**
     * Gestore generico per tutte le eccezioni non gestite specificamente.
     *
     * @param ex l'eccezione catturata
     * @return una {@link ResponseEntity} con status 500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Void> handleException(Exception ex) {
        log.error("Unhandled exception: ", ex);
        return ResponseEntity.internalServerError().build();
    }
}
