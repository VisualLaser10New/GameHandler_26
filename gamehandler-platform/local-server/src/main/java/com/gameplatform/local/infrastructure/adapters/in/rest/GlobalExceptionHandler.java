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

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Void> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Void> handleUserNotFound(UserNotFoundException ex) {
        log.warn("User not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Void> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Invalid credentials: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<Void> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        log.warn("Conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Void> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @ExceptionHandler(ConcurrentStateException.class)
    public ResponseEntity<Void> handleConcurrentState(ConcurrentStateException ex) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @ExceptionHandler(BuildingNotRegisteredToAdminException.class)
    public ResponseEntity<Void> handleBuildingNotRegistered(BuildingNotRegisteredToAdminException ex) {
        log.warn("Building not registered to admin: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @ExceptionHandler(GameDefinitionNotAvailableLocallyException.class)
    public ResponseEntity<Void> handleGameDefinitionNotAvailableLocally(GameDefinitionNotAvailableLocallyException ex) {
        log.warn("Game definition not available locally: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }

    @ExceptionHandler(TournamentMatchNotFoundException.class)
    public ResponseEntity<Void> handleTournamentMatchNotFound(TournamentMatchNotFoundException ex) {
        log.warn("Tournament match not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @ExceptionHandler(TournamentMatchNotScheduledException.class)
    public ResponseEntity<Void> handleTournamentMatchNotScheduled(TournamentMatchNotScheduledException ex) {
        log.warn("Tournament match not scheduled: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @ExceptionHandler(TournamentMatchBuildingMismatchException.class)
    public ResponseEntity<Void> handleTournamentMatchBuildingMismatch(TournamentMatchBuildingMismatchException ex) {
        log.warn("Tournament match building mismatch: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @ExceptionHandler(TournamentMatchValidationException.class)
    public ResponseEntity<Void> handleTournamentMatchValidation(TournamentMatchValidationException ex) {
        log.warn("Tournament match validation error: {}", ex.getMessage());
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Void> handleException(Exception ex) {
        log.error("Unhandled exception: ", ex);
        return ResponseEntity.internalServerError().build();
    }
}
