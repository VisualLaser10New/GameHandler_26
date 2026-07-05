package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.ports.in.GetAvailableGamesUseCase;
import com.gameplatform.shared.dto.GameStateDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/games")
@PreAuthorize("hasRole('USER')")
public class GameController {

    private final GetAvailableGamesUseCase getAvailableGamesUseCase;

    public GameController(GetAvailableGamesUseCase getAvailableGamesUseCase) {
        this.getAvailableGamesUseCase = getAvailableGamesUseCase;
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

    private GameStateDto toDto(Game game) {
        return new GameStateDto(
                game.getId().id(),
                game.getGameType(),
                game.getName(),
                game.getBuildingId().id(),
                game.getStatus()
        );
    }
}
