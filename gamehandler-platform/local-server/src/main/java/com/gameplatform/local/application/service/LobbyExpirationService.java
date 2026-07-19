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

/**
 * Servizio schedulato che gestisce la scadenza delle lobby rimaste in
 * stato WAITING oltre il tempo di inattivita' configurato (default 10
 * minuti). Alla scadenza, la sessione viene cancellata e la macchina
 * da gioco viene rilasciata a AVAILABLE. La pubblicazione MQTT dello
 * stato AVAILABLE e dell'evento lobby/cancel notifica ai giocatori
 * in attesa di abbandonare la schermata di lobby.
 */
@Service
@Transactional
public class LobbyExpirationService {

    private static final Logger log = LoggerFactory.getLogger(LobbyExpirationService.class);

    private final GameSessionRepository gameSessionRepository;
    private final GameRepository gameRepository;
    private final PublishGameStatePort publishGameStatePort;
    private final Clock clock;
    private final long lobbyExpirationMinutes;

    public LobbyExpirationService(
            GameSessionRepository gameSessionRepository,
            GameRepository gameRepository,
            PublishGameStatePort publishGameStatePort,
            Clock clock,
            @org.springframework.beans.factory.annotation.Value("${app.lobby.expiration-minutes:10}") long lobbyExpirationMinutes) {
        this.gameSessionRepository = gameSessionRepository;
        this.gameRepository = gameRepository;
        this.publishGameStatePort = publishGameStatePort;
        this.clock = clock;
        this.lobbyExpirationMinutes = lobbyExpirationMinutes;
    }

    /**
     * Sweep minuto delle lobby in stato WAITING. Per ogni lobby la cui
     * finestra di inattivita' e' scaduta, cancella la sessione, rilascia
     * la macchina da gioco e pubblica gli eventi MQTT.
     */
    @Scheduled(fixedRate = 60000)
    public void expireLobbies() {
        log.debug("Checking for expired lobbies...");
        List<GameSession> lobbies = gameSessionRepository.findByStatus(GameStatus.WAITING);
        Instant now = Instant.now(clock);

        for (GameSession session : lobbies) {
            Instant startedAt = session.getStartedAt();
            // Lobbies are expired after lobbyExpirationMinutes of inactivity
            // (default 2 min, configurable via app.lobby.expiration-minutes).
            // This is intentionally short: if the creator navigated away
            // without explicitly cancelling (e.g. client crash, race condition
            // on lobbySessionId), the game machine should return to AVAILABLE
            // quickly so other players can use it.
            if (startedAt != null && startedAt.plus(lobbyExpirationMinutes, ChronoUnit.MINUTES).isBefore(now)) {
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

                    // Publish to MQTT — mirror the user-initiated cancel path
                    // (GameSessionService.cancelLobby): publish the AVAILABLE
                    // game state AND a lobby/cancel session event so the
                    // joiners currently waiting in the LobbyView auto-navigate
                    // away via their case "cancel" handler. Without this event
                    // the timer would release the game machine but leave the
                    // joiners stuck on the lobby screen (only the state topic
                    // fires, which LobbyView.handleStateMqttMessage doesn't
                    // translate into a navigation for status == AVAILABLE).
                    // Note: the publishing server is also a subscriber to
                    // session/# (see MqttConfig), so it receives its own echo;
                    // the OutboundMessageDeduplicationCache skips that echo
                    // (fingerprint match), so the server does not try to
                    // reprocess its own lobby/cancel (which is a raw GameSession
                    // payload, not a LobbyCancelPayload).
                    String cancelTopic = "building/" + session.getBuildingId().id()
                            + "/game/" + session.getGameId().id()
                            + "/session/lobby/cancel";
                    if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
                        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                            new org.springframework.transaction.support.TransactionSynchronization() {
                                @Override
                                public void afterCommit() {
                                    try {
                                        publishGameStatePort.publishState(game.getId(), game.getStatus());
                                        publishGameStatePort.publishSessionEvent(cancelTopic, session);
                                    } catch (Exception e) {
                                        log.error("Failed to publish game state after lobby expiration transaction commit", e);
                                    }
                                }
                            }
                        );
                    } else {
                        publishGameStatePort.publishState(game.getId(), game.getStatus());
                        publishGameStatePort.publishSessionEvent(cancelTopic, session);
                    }
                }
            }
        }
    }
}
