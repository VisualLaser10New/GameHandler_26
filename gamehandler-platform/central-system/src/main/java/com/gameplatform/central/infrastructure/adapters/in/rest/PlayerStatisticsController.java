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

    public PlayerStatisticsController(GetPlayerStatisticsUseCase getPlayerStatisticsUseCase,
                                      CurrentUserService currentUserService) {
        this.getPlayerStatisticsUseCase = getPlayerStatisticsUseCase;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/me/statistics")
    @PreAuthorize("hasRole('PLAYER') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<PlayerStatisticsDto>> getMyStatistics(
            @RequestParam(value = "gameType", required = false) String gameType) {
        UserId userId = requireCurrentUserId();
        GameType parsedGameType = parseGameType(gameType);
        return ResponseEntity.ok(getPlayerStatisticsUseCase.getStatistics(userId, parsedGameType));
    }

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

    private UserId requireCurrentUserId() {
        return currentUserService.getCurrentUserId()
                .orElseThrow(() -> new PlayerStatisticsAccessDeniedException(
                        "Authenticated user could not be resolved"));
    }

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