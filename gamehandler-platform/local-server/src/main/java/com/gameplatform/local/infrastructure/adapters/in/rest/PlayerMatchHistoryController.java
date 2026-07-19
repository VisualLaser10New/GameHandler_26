package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.application.service.ListPlayerMatchesService;
import com.gameplatform.local.domain.ports.in.ListPlayerMatchesUseCase;
import com.gameplatform.local.infrastructure.security.CurrentUserService;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.PlayerMatchDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * PLAYER read endpoint (PIANO §7.B): returns the COMPLETED game-session
 * history for the currently authenticated user, optionally filtered by
 * {@code gameType}. Delegates the COMPLETED filter to
 * {@link ListPlayerMatchesService}; the {@code actingUserId} is resolved
 * via {@link CurrentUserService}.
 */
@RestController
@RequestMapping("/api/players/me/matches")
@PreAuthorize("hasRole('PLAYER') or hasRole('PLATFORM_ADMIN')")
public class PlayerMatchHistoryController {

    private final ListPlayerMatchesUseCase listPlayerMatchesUseCase;
    private final CurrentUserService currentUserService;

    /**
     * Costruisce il controller con il caso d'uso per la cronologia partite
     * e il servizio per l'utente corrente.
     *
     * @param listPlayerMatchesUseCase caso d'uso per la lista delle partite del giocatore
     * @param currentUserService servizio per la risoluzione dell'utente autenticato
     */
    public PlayerMatchHistoryController(ListPlayerMatchesUseCase listPlayerMatchesUseCase,
                                         CurrentUserService currentUserService) {
        this.listPlayerMatchesUseCase = listPlayerMatchesUseCase;
        this.currentUserService = currentUserService;
    }

    /**
     * Restituisce la cronologia delle partite completate per l'utente
     * autenticato, opzionalmente filtrata per tipo di gioco.
     *
     * @param gameType filtro opzionale per tipo di gioco
     * @return una {@link ResponseEntity} con la lista di {@link PlayerMatchDto}
     * @throws IllegalArgumentException se il gameType fornito non è valido
     */
    @GetMapping("/history")
    public ResponseEntity<List<PlayerMatchDto>> myMatchHistory(
            @RequestParam(name = "gameType", required = false) String gameType) {
        Optional<UserId> currentUserId = currentUserService.getCurrentUserId();
        if (currentUserId.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        GameType filter = null;
        if (gameType != null && !gameType.isBlank()) {
            try {
                filter = GameType.valueOf(gameType.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Unknown game type: '" + gameType
                        + "'. Valid values are: " + java.util.Arrays.toString(GameType.values()));
            }
        }
        List<PlayerMatchDto> result = listPlayerMatchesUseCase.listCompletedMatches(currentUserId.get(), filter);
        return ResponseEntity.ok(result);
    }
}