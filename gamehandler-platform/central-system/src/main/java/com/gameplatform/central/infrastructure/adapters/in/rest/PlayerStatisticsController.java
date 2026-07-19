package com.gameplatform.central.infrastructure.adapters.in.rest;

import com.gameplatform.central.domain.exception.PlayerStatisticsAccessDeniedException;
import com.gameplatform.central.domain.ports.in.GetPlayerStatisticsUseCase;
import com.gameplatform.central.infrastructure.security.CurrentUserService;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.security.Role;
import com.gameplatform.shared.dto.PlayerStatisticsDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * REST adapter exposing a player's personal statistics (FASE 3, PIANO
 * &sect;2.4). The Central is the global source of truth for player statistics
 * (the {@code player_statistics} read-model projected by the
 * {@code SyncEventProcessor}).
 *
 * <ul>
 *   <li>{@code GET /api/players/me/statistics} &mdash; the authenticated
 *       player's own statistics ({@code PLAYER} role); optional
 *       {@code ?gameType=} filter.</li>
 *   <li>{@code GET /api/players/{userId}/statistics} &mdash; any player's
 *       statistics, authorised for a {@code PLATFORM_ADMIN} or for the
 *       principal viewing their own data (self-check); optional
 *       {@code ?gameType=} filter.</li>
 * </ul>
 *
 * <p>The authenticated user id is recovered via {@link CurrentUserService}
 * (username &rarr; {@link UserId}). Unauthorised access to another player's
 * statistics raises {@link PlayerStatisticsAccessDeniedException}, mapped to
 * HTTP 403 by {@link GlobalExceptionHandler}. An unknown {@code gameType}
 * parameter is re-thrown as {@link IllegalArgumentException} &rarr; 400.</p>
 */
@RestController
@RequestMapping("/api/players")
public class PlayerStatisticsController {

    private final GetPlayerStatisticsUseCase getPlayerStatisticsUseCase;
    private final CurrentUserService currentUserService;

    /**
     * Costruisce il controller iniettando il caso d'uso e il servizio utente corrente.
     *
     * @param getPlayerStatisticsUseCase caso d'uso per la lettura delle statistiche, non {@code null}
     * @param currentUserService         servizio per la risoluzione dell'utente autenticato, non {@code null}
     */
    public PlayerStatisticsController(GetPlayerStatisticsUseCase getPlayerStatisticsUseCase,
                                       CurrentUserService currentUserService) {
        this.getPlayerStatisticsUseCase = getPlayerStatisticsUseCase;
        this.currentUserService = currentUserService;
    }

    /**
     * Restituisce le statistiche del giocatore attualmente autenticato.
     *
     * <p>Restituisce le statistiche filtrate opzionalmente per tipo di gioco. L'accesso
     * richiede il ruolo {@code PLAYER} o {@code PLATFORM_ADMIN}.</p>
     *
     * @param gameType tipo di gioco opzionale per filtrare le statistiche; se {@code null} o vuoto,
     *                 restituisce le statistiche per tutti i giochi
     * @return {@link ResponseEntity} con stato {@code 200 OK} e la lista di {@link PlayerStatisticsDto};
     *         la lista è vuota se non esiste alcuna statistica
     * @throws PlayerStatisticsAccessDeniedException se l'utente autenticato non può essere risolto (mappato a {@code 403})
     * @throws IllegalArgumentException se {@code gameType} non corrisponde a un valore valido (mappato a {@code 400})
     * @see GlobalExceptionHandler
     */
    @GetMapping("/me/statistics")
    @PreAuthorize("hasRole('PLAYER') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<PlayerStatisticsDto>> getMyStatistics(
            @RequestParam(value = "gameType", required = false) String gameType) {
        UserId userId = requireCurrentUserId();
        GameType parsedGameType = parseGameType(gameType);
        return ResponseEntity.ok(getPlayerStatisticsUseCase.getStatistics(userId, parsedGameType));
    }

    /**
     * Restituisce le statistiche del giocatore identificato dall'identificativo fornito.
     *
     * <p>L'accesso è consentito a un {@code PLATFORM_ADMIN} oppure al giocatore stesso
     * (verifica di identità). Le statistiche possono essere filtrate opzionalmente per tipo di gioco.</p>
     *
     * @param userId   identificativo del giocatore di cui leggere le statistiche, non {@code null} né vuoto
     * @param gameType tipo di gioco opzionale per filtrare le statistiche; se {@code null} o vuoto,
     *                 restituisce le statistiche per tutti i giochi
     * @return {@link ResponseEntity} con stato {@code 200 OK} e la lista di {@link PlayerStatisticsDto};
     *         la lista è vuota se non esiste alcuna statistica
     * @throws PlayerStatisticsAccessDeniedException se il chiamante non è autorizzato a vedere le statistiche
     *                                               del giocatore indicato, o l'utente corrente non è risolvibile (mappato a {@code 403})
     * @throws IllegalArgumentException se {@code gameType} non corrisponde a un valore valido (mappato a {@code 400})
     * @see GlobalExceptionHandler
     */
    @GetMapping("/{userId}/statistics")
    public ResponseEntity<List<PlayerStatisticsDto>> getUserStatistics(
            @PathVariable String userId,
            @RequestParam(value = "gameType", required = false) String gameType) {
        UserId targetUserId = new UserId(userId);
        UserId currentUserId = requireCurrentUserId();
        boolean isPlatformAdmin = currentUserService.hasRole(Role.PLATFORM_ADMIN.name());
        if (!isPlatformAdmin && !currentUserId.equals(targetUserId)) {
            throw new PlayerStatisticsAccessDeniedException(
                    "Access denied: cannot view statistics for user " + userId);
        }
        GameType parsedGameType = parseGameType(gameType);
        return ResponseEntity.ok(getPlayerStatisticsUseCase.getStatistics(targetUserId, parsedGameType));
    }

    /**
     * Risolve l'identificativo dell'utente attualmente autenticato.
     *
     * @return l'{@link UserId} dell'utente corrente
     * @throws PlayerStatisticsAccessDeniedException se l'utente autenticato non può essere risolto
     */
    private UserId requireCurrentUserId() {
        return currentUserService.getCurrentUserId()
                .orElseThrow(() -> new PlayerStatisticsAccessDeniedException(
                        "Authenticated user could not be resolved"));
    }

    /**
     * Converte una stringa in un {@link GameType} di dominio.
     *
     * @param gameType stringa opzionale rappresentante il tipo di gioco; se {@code null} o vuota restituisce {@code null}
     * @return il {@link GameType} corrispondente, o {@code null} se l'input non è fornito
     * @throws IllegalArgumentException se la stringa non corrisponde a nessun valore valido di {@link GameType}
     */
    private static GameType parseGameType(String gameType) {
        if (gameType == null || gameType.isBlank()) {
            return null;
        }
        try {
            return GameType.valueOf(gameType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown game type: '" + gameType + "'. Valid values are: "
                            + Arrays.toString(GameType.values()));
        }
    }
}