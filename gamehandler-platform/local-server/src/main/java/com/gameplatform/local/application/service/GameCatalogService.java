package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.exception.GameNotAvailableException;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.ports.in.ManageGameCatalogUseCase;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.domain.model.GameType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Application service backing the LOCAL_ADMIN game-catalog CRUD endpoints
 * ({@code POST/PUT/DELETE /api/admin/local/games}). Enforces the domain state-
 * transition invariants via {@link Game} methods and uses the pessimistic
 * {@link GameRepository#findByIdForUpdate} lock on update (consistency with the
 * existing {@code GameStateService.updateState} pessimistic-read pattern).
 */
@Service
@Transactional
public class GameCatalogService implements ManageGameCatalogUseCase {

    private final GameRepository gameRepository;

    public GameCatalogService(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

    @Override
    public Game createGame(GameType gameType, String name, BuildingId buildingId) {
        if (gameType == null) {
            throw new IllegalArgumentException("GameType cannot be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be null, empty or blank");
        }
        if (buildingId == null) {
            throw new IllegalArgumentException("BuildingId cannot be null");
        }
        GameId id = new GameId(UUID.randomUUID().toString());
        Game game = new Game(id, gameType, name, buildingId, GameMachineStatus.AVAILABLE);
        return gameRepository.save(game);
    }

    @Override
    public Game updateGame(GameId gameId, String newName, GameMachineStatus newStatus) {
        if (gameId == null) {
            throw new IllegalArgumentException("GameId cannot be null");
        }
        boolean hasName = newName != null && !newName.isBlank();
        boolean hasStatus = newStatus != null;
        if (!hasName && !hasStatus) {
            throw new IllegalArgumentException("At least one of name or status must be provided");
        }
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found: " + gameId.id()));
        if (hasName) {
            game.rename(newName);
        }
        if (hasStatus) {
            if (newStatus == GameMachineStatus.MAINTENANCE) {
                game.setMaintenance();
            } else if (newStatus == GameMachineStatus.AVAILABLE) {
                game.release();
            } else {
                throw new IllegalArgumentException(
                        "Admin can only set status to AVAILABLE or MAINTENANCE, got: " + newStatus);
            }
        }
        return gameRepository.save(game);
    }

    @Override
    public void deleteGame(GameId gameId) {
        if (gameId == null) {
            throw new IllegalArgumentException("GameId cannot be null");
        }
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found: " + gameId.id()));
        if (game.getStatus() == GameMachineStatus.IN_USE) {
            throw new GameNotAvailableException(
                    "Cannot delete game " + gameId.id() + " while IN_USE");
        }
        gameRepository.deleteById(gameId);
    }
}