package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.StopReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Transactional
public class LobbyExpirationService {

    private static final Logger log = LoggerFactory.getLogger(LobbyExpirationService.class);

    private final GameSessionRepository gameSessionRepository;
    private final GameRepository gameRepository;
    private final PublishGameStatePort publishGameStatePort;
    private final Clock clock;

    public LobbyExpirationService(
            GameSessionRepository gameSessionRepository,
            GameRepository gameRepository,
            PublishGameStatePort publishGameStatePort,
            Clock clock) {
        this.gameSessionRepository = gameSessionRepository;
        this.gameRepository = gameRepository;
        this.publishGameStatePort = publishGameStatePort;
        this.clock = clock;
    }

    @Scheduled(fixedRate = 60000)
    public void expireLobbies() {
        log.debug("Checking for expired lobbies...");
        List<GameSession> lobbies = gameSessionRepository.findByStatus(GameStatus.WAITING);
        Instant now = Instant.now(clock);

        for (GameSession session : lobbies) {
            Instant startedAt = session.getStartedAt();
            // Lobbies are expired after 2 minutes of inactivity. This is
            // intentionally short: if the creator navigated away without
            // explicitly cancelling (e.g. client crash, race condition on
            // lobbySessionId), the game machine should return to AVAILABLE
            // quickly so other players can use it.
            if (startedAt != null && startedAt.plus(2, ChronoUnit.MINUTES).isBefore(now)) {
                log.info("Lobby {} has expired. Aborting session and releasing game machine {}.", 
                        session.getId().value(), session.getGameId().id());
                
                // Cancel the lobby session
                session.cancelLobby(now);
                gameSessionRepository.save(session);

                // Release the game machine
                Game game = gameRepository.findByIdForUpdate(session.getGameId()).orElse(null);
                if (game != null) {
                    game.release();
                    gameRepository.save(game);

                    // Publish to MQTT
                    if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
                        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                            new org.springframework.transaction.support.TransactionSynchronization() {
                                @Override
                                public void afterCommit() {
                                    try {
                                        publishGameStatePort.publishState(game.getId(), game.getStatus());
                                    } catch (Exception e) {
                                        log.error("Failed to publish game state after lobby expiration transaction commit", e);
                                    }
                                }
                            }
                        );
                    } else {
                        publishGameStatePort.publishState(game.getId(), game.getStatus());
                    }
                }
            }
        }
    }
}
