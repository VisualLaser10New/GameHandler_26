package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.domain.ports.in.GetPlayerStatisticsUseCase;
import com.gameplatform.local.infrastructure.security.CurrentUserService;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.PlayerStatisticsDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * REST adapter exposing the authenticated player's personal statistics computed
 * on-demand from the Local {@code game_sessions}+{@code session_participants}
 * tables (FASE 3, PIANO &sect;2.5). The Local server is an offline-capable
 * replica of the Central read-model: no additional sync is required.
 *
 * <ul>
 *   <li>{@code GET /api/players/me/statistics} &mdash; the authenticated
 *       player's own statistics ({@code PLAYER} role); optional
 *       {@code ?gameType=} filter.</li>
 * </ul>
 *
 * <p>The authenticated user id is recovered via {@link CurrentUserService}
 * (username &rarr; {@link UserId}). When the user cannot be resolved locally
 * (not yet replicated on this building's server) an <em>empty</em> list is
 * returned &mdash; semantically equivalent to "the player has played no
 * matches on this local server". An unknown {@code gameType} parameter is
 * re-thrown as {@link IllegalArgumentException} &rarr; 400.</p>
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
    @PreAuthorize("hasRole('PLAYER')")
    public ResponseEntity<List<PlayerStatisticsDto>> getMyStatistics(
            @RequestParam(value = "gameType", required = false) String gameType) {
        UserId userId = currentUserService.getCurrentUserId().orElse(null);
        if (userId == null) {
            // Authenticated but not replicated locally (offline-first): no local
            // matches exist for this player on this server.
            return ResponseEntity.ok(List.of());
        }
        List<PlayerStatisticsDto> all = getPlayerStatisticsUseCase.getPlayerStatistics(userId);
        GameType parsedGameType = parseGameType(gameType);
        if (parsedGameType == null) {
            return ResponseEntity.ok(all);
        }
        return ResponseEntity.ok(all.stream()
                .filter(dto -> dto.gameType() == parsedGameType)
                .toList());
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