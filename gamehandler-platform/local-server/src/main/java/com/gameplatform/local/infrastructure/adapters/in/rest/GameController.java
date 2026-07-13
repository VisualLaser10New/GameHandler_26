package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.domain.model.GameDefinitionLocal;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.ports.in.GetAvailableGamesUseCase;
import com.gameplatform.local.domain.ports.out.GameDefinitionLocalRepository;
import com.gameplatform.shared.domain.game.GameFactory;
import com.gameplatform.shared.domain.game.GameLifecycle;
import com.gameplatform.shared.dto.GameStateDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.gameplatform.shared.domain.security.Role;

import java.util.List;

@RestController
@RequestMapping("/api/games")
@PreAuthorize("hasRole('PLAYER') or hasRole('GAME_ADMIN') or hasRole('PLATFORM_ADMIN') or hasRole('LOCAL_ADMIN')")
public class GameController {

    private final GetAvailableGamesUseCase getAvailableGamesUseCase;
    private final GameDefinitionLocalRepository gameDefinitionLocalRepository;

    public GameController(GetAvailableGamesUseCase getAvailableGamesUseCase,
                          GameDefinitionLocalRepository gameDefinitionLocalRepository) {
        this.getAvailableGamesUseCase = getAvailableGamesUseCase;
        this.gameDefinitionLocalRepository = gameDefinitionLocalRepository;
    }

    @GetMapping
    public ResponseEntity<List<GameStateDto>> getGames() {
        List<Game> games = getAvailableGamesUseCase.getAll();
        List<GameStateDto> dtos = games.stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/available")
    public ResponseEntity<List<GameStateDto>> getAvailableGames() {
        List<Game> games = getAvailableGamesUseCase.getAvailable();
        List<GameStateDto> dtos = games.stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Projects a {@link Game} into the {@link GameStateDto} response, reading
     * {@code minPlayers}/{@code maxPlayers} from the locally replicated
     * {@code game_definitions_local} table (PIANO §7.B allineamento) — falls
     * back to the static {@link GameLifecycle} defaults when no definition
     * is replicated locally yet (offline-first / pre-replication window).
     */
    private GameStateDto toDto(Game game) {
        int minPlayers;
        int maxPlayers;
        GameDefinitionLocal def = gameDefinitionLocalRepository.findByGameType(game.getGameType()).orElse(null);
        if (def != null) {
            minPlayers = def.getMinPlayers();
            maxPlayers = def.getMaxPlayers();
        } else {
            GameLifecycle lifecycle = GameFactory.createGame(game.getGameType(), null);
            minPlayers = lifecycle.getMinPlayers();
            maxPlayers = lifecycle.getMaxPlayers();
        }
        return new GameStateDto(
                game.getId().id(),
                game.getGameType(),
                game.getName(),
                game.getBuildingId().id(),
                game.getStatus(),
                minPlayers,
                maxPlayers
        );
    }
}
