package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.exception.GameNotAvailableException;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.ports.in.GetAvailableGamesUseCase;
import com.gameplatform.local.domain.ports.in.UpdateGameStateUseCase;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class GameStateService implements UpdateGameStateUseCase, GetAvailableGamesUseCase {

    private final GameRepository gameRepository;
    private final PublishGameStatePort publishGameStatePort;

    public GameStateService(GameRepository gameRepository, PublishGameStatePort publishGameStatePort) {
        this.gameRepository = gameRepository;
        this.publishGameStatePort = publishGameStatePort;
    }

    @Override
    public void updateState(GameId gameId, GameMachineStatus newStatus) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new GameNotAvailableException("Game machine not found: " + gameId.id()));

        GameMachineStatus previousStatus = game.getStatus();

        // Enforce Clean Architecture state validation via domain methods
        switch (newStatus) {
            case AVAILABLE -> game.release();
            case RESERVED -> game.reserve();
            case IN_USE -> game.startUse();
            case MAINTENANCE -> game.setMaintenance();
        }

        // Idempotency: skip persistence and MQTT re-publish when the status is
        // unchanged. The local-server is subscribed to the same
        // building/{id}/game/+/state topic it publishes to, so echoing a
        // no-op transition (e.g. AVAILABLE -> AVAILABLE, since Game.release()
        // returns silently when already AVAILABLE) would otherwise cause an
        // infinite MQTT echo loop hammering the database.
        if (game.getStatus() == previousStatus) {
            return;
        }

        gameRepository.save(game);
        publishGameStatePort.publishState(gameId, game.getStatus());
    }

    @Override
    public List<Game> getAvailable() {
        return gameRepository.findByStatus(GameMachineStatus.AVAILABLE);
    }

    @Override
    public List<Game> getAll() {
        return gameRepository.findAll();
    }
}
