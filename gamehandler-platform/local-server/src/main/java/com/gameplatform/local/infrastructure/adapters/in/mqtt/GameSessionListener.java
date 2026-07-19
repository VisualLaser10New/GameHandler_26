package com.gameplatform.local.infrastructure.adapters.in.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.ports.in.EndGameSessionUseCase;
import com.gameplatform.local.domain.ports.in.PauseGameSessionUseCase;
import com.gameplatform.local.domain.ports.in.ResumeGameSessionUseCase;
import com.gameplatform.local.domain.ports.in.StartGameSessionUseCase;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;
import com.gameplatform.shared.domain.result.GameResult;
import com.gameplatform.shared.mqtt.MqttPayloadSerializer;
import com.gameplatform.shared.mqtt.payload.SessionEndPayload;
import com.gameplatform.shared.mqtt.payload.SessionPausePayload;
import com.gameplatform.shared.mqtt.payload.SessionResumePayload;
import com.gameplatform.shared.mqtt.payload.SessionStartPayload;
import com.gameplatform.local.domain.ports.in.CreateLobbyUseCase;
import com.gameplatform.local.domain.ports.in.JoinLobbyUseCase;
import com.gameplatform.local.domain.ports.in.StartLobbyUseCase;
import com.gameplatform.local.domain.ports.in.CancelLobbyUseCase;
import com.gameplatform.local.domain.ports.in.LeaveLobbyUseCase;
import com.gameplatform.shared.mqtt.payload.LobbyCreatePayload;
import com.gameplatform.shared.mqtt.payload.LobbyJoinPayload;
import com.gameplatform.shared.mqtt.payload.LobbyLeavePayload;
import com.gameplatform.shared.mqtt.payload.LobbyStartPayload;
import com.gameplatform.shared.mqtt.payload.LobbyCancelPayload;
import com.gameplatform.shared.domain.model.GameType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Listener MQTT per i messaggi relativi al ciclo di vita delle sessioni di gioco e alla gestione delle lobby.
 * <p>
 * Smista i payload MQTT in base alla struttura del topic verso gli use case dedicati per le operazioni
 * di avvio, pausa, ripresa e termine di una sessione, nonché per la creazione, iscrizione, avvio,
 * cancellazione e abbandono di una lobby.
 * </p>
 *
 * @see StartGameSessionUseCase
 * @see EndGameSessionUseCase
 * @see PauseGameSessionUseCase
 * @see ResumeGameSessionUseCase
 * @see CreateLobbyUseCase
 * @see JoinLobbyUseCase
 * @see StartLobbyUseCase
 * @see CancelLobbyUseCase
 * @see LeaveLobbyUseCase
 */
@Component
public class GameSessionListener {

    private final StartGameSessionUseCase startGameSessionUseCase;
    private final EndGameSessionUseCase endGameSessionUseCase;
    private final PauseGameSessionUseCase pauseGameSessionUseCase;
    private final ResumeGameSessionUseCase resumeGameSessionUseCase;
    private final CreateLobbyUseCase createLobbyUseCase;
    private final JoinLobbyUseCase joinLobbyUseCase;
    private final StartLobbyUseCase startLobbyUseCase;
    private final CancelLobbyUseCase cancelLobbyUseCase;
    private final LeaveLobbyUseCase leaveLobbyUseCase;
    private final ObjectMapper objectMapper;

    /**
     * Costruisce un nuovo listener iniettando i casi d'uso necessari per la gestione delle sessioni
     * di gioco e delle lobby, e il mapper JSON per la deserializzazione di payload complessi.
     *
     * @param startGameSessionUseCase caso d'uso per l'avvio di una sessione
     * @param endGameSessionUseCase   caso d'uso per la chiusura di una sessione
     * @param pauseGameSessionUseCase caso d'uso per la pausa di una sessione
     * @param resumeGameSessionUseCase caso d'uso per la ripresa di una sessione
     * @param createLobbyUseCase      caso d'uso per la creazione di una lobby
     * @param joinLobbyUseCase        caso d'uso per l'ingresso in una lobby
     * @param startLobbyUseCase       caso d'uso per l'avvio della partita dalla lobby
     * @param cancelLobbyUseCase      caso d'uso per la cancellazione di una lobby
     * @param leaveLobbyUseCase       caso d'uso per l'abbandono di una lobby
     * @param objectMapper            mapper per la deserializzazione di oggetti JSON complessi
     */
    public GameSessionListener(
            StartGameSessionUseCase startGameSessionUseCase,
            EndGameSessionUseCase endGameSessionUseCase,
            PauseGameSessionUseCase pauseGameSessionUseCase,
            ResumeGameSessionUseCase resumeGameSessionUseCase,
            CreateLobbyUseCase createLobbyUseCase,
            JoinLobbyUseCase joinLobbyUseCase,
            StartLobbyUseCase startLobbyUseCase,
            CancelLobbyUseCase cancelLobbyUseCase,
            LeaveLobbyUseCase leaveLobbyUseCase,
            ObjectMapper objectMapper) {
        this.startGameSessionUseCase = startGameSessionUseCase;
        this.endGameSessionUseCase = endGameSessionUseCase;
        this.pauseGameSessionUseCase = pauseGameSessionUseCase;
        this.resumeGameSessionUseCase = resumeGameSessionUseCase;
        this.createLobbyUseCase = createLobbyUseCase;
        this.joinLobbyUseCase = joinLobbyUseCase;
        this.startLobbyUseCase = startLobbyUseCase;
        this.cancelLobbyUseCase = cancelLobbyUseCase;
        this.leaveLobbyUseCase = leaveLobbyUseCase;
        this.objectMapper = objectMapper;
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GameSessionListener.class);

    /**
     * Elabora un messaggio MQTT in arrivo sul topic delle sessioni di gioco, interpreta l'azione
     * richiesta dalla struttura del topic e delega l'esecuzione allo use case corrispondente.
     * <p>
     * Le azioni supportate includono la gestione delle lobby (create, join, leave, start, cancel)
     * e le operazioni sulle sessioni (start, end, pause, resume).
     * </p>
     *
     * @param topic   topic MQTT dal quale estrarre l'identificativo del gioco e l'azione richiesta
     * @param payload payload del messaggio contenente i dati specifici per l'azione
     * @throws com.gameplatform.local.domain.exception.InvalidGameStateTransitionException se la
     *         transizione di stato richiesta non è valida per lo stato corrente (gestita internamente)
     * @throws com.gameplatform.local.domain.exception.ConcurrentStateException se un'altra
     *         transizione concorrente ha già modificato lo stato (gestita internamente)
     * @see MqttPayloadSerializer#deserialize(byte[], Class)
     */
    public void handleSessionMessage(String topic, byte[] payload) {
        String[] tokens = topic.split("/");
        String gameId = tokens[3];
        String action = tokens[5];
        log.info("[GameSessionListener] RECV topic={} payload={}", topic, new String(payload, java.nio.charset.StandardCharsets.UTF_8));

        try {
            switch (action) {
                case "lobby" -> {
                    if (tokens.length >= 7) {
                        String lobbyAction = tokens[6];
                        switch (lobbyAction) {
                            case "create" -> {
                                LobbyCreatePayload payloadDto = MqttPayloadSerializer.deserialize(payload, LobbyCreatePayload.class);
                                log.info("[GameSessionListener] CREATE gameId={} creator={}", gameId, payloadDto.creatorId());
                                createLobbyUseCase.createLobby(new GameId(gameId), payloadDto.gameType(), new UserId(payloadDto.creatorId()));
                            }
                            case "join" -> {
                                LobbyJoinPayload payloadDto = MqttPayloadSerializer.deserialize(payload, LobbyJoinPayload.class);
                                log.info("[GameSessionListener] JOIN sessionId={} userId={}", payloadDto.sessionId(), payloadDto.userId());
                                joinLobbyUseCase.joinLobby(new GameSessionId(payloadDto.sessionId()), new UserId(payloadDto.userId()));
                            }
                            case "leave" -> {
                                LobbyLeavePayload payloadDto = MqttPayloadSerializer.deserialize(payload, LobbyLeavePayload.class);
                                leaveLobbyUseCase.leaveLobby(new GameSessionId(payloadDto.sessionId()), new UserId(payloadDto.userId()));
                            }
                            case "start" -> {
                                LobbyStartPayload payloadDto = MqttPayloadSerializer.deserialize(payload, LobbyStartPayload.class);
                                startLobbyUseCase.startLobby(new GameSessionId(payloadDto.sessionId()));
                            }
                            case "cancel" -> {
                                LobbyCancelPayload payloadDto = MqttPayloadSerializer.deserialize(payload, LobbyCancelPayload.class);
                                cancelLobbyUseCase.cancelLobby(new GameSessionId(payloadDto.sessionId()), new UserId(payloadDto.userId()));
                            }
                        }
                    }
                }
                case "start" -> {
                    SessionStartPayload startPayload = MqttPayloadSerializer.deserialize(payload, SessionStartPayload.class);
                    List<UserId> participants = startPayload.participants() != null
                            ? startPayload.participants().stream().map(UserId::new).toList()
                            : List.of();
                    startGameSessionUseCase.start(new GameId(gameId), startPayload.gameType(), participants, null);
                }
                case "end" -> {
                    SessionEndPayload endPayload = MqttPayloadSerializer.deserialize(payload, SessionEndPayload.class);
                    GameResult result = null;
                    if (endPayload.resultData() != null && !endPayload.resultData().isBlank()) {
                        try {
                            result = objectMapper.readValue(endPayload.resultData(), GameResult.class);
                        } catch (Exception e) {
                            // Fallback mapping on parsing exception
                        }
                    }
                    if (result == null) {
                        final String winnerIdVal = endPayload.winnerId();
                        final WinCondition winConditionVal = endPayload.winCondition();
                        result = new GameResult() {
                            @Override
                            public UserId getWinnerId() {
                                return winnerIdVal != null ? new UserId(winnerIdVal) : null;
                            }
                            @Override
                            public List<UserId> getWinnerIds() {
                                return winnerIdVal != null ? List.of(new UserId(winnerIdVal)) : List.of();
                            }
                            @Override
                            public WinCondition getWinCondition() {
                                return winConditionVal;
                            }
                        };
                    }
                    endGameSessionUseCase.end(new GameSessionId(endPayload.sessionId()), result);
                }
                case "pause" -> {
                    SessionPausePayload pausePayload = MqttPayloadSerializer.deserialize(payload, SessionPausePayload.class);
                    pauseGameSessionUseCase.pause(new GameSessionId(pausePayload.sessionId()));
                }
                case "resume" -> {
                    SessionResumePayload resumePayload = MqttPayloadSerializer.deserialize(payload, SessionResumePayload.class);
                    if (resumePayload.sessionId() == null || resumePayload.sessionId().isBlank()) {
                        org.slf4j.LoggerFactory.getLogger(GameSessionListener.class)
                                .warn("Ignoring resume message on topic {} with missing sessionId", topic);
                        return;
                    }
                    resumeGameSessionUseCase.resume(new GameSessionId(resumePayload.sessionId()));
                }
            }
        } catch (com.gameplatform.local.domain.exception.InvalidGameStateTransitionException
                | com.gameplatform.local.domain.exception.SessionAlreadyActiveException e) {
            // MQTT QoS 1 may redeliver messages; the first delivery already
            // applied the state change, so a redelivery hitting the same
            // state is a no-op, not an error.
            org.slf4j.LoggerFactory.getLogger(GameSessionListener.class)
                    .debug("Ignoring idempotent/no-op session message on topic {}: {}", topic, e.getMessage());
        } catch (com.gameplatform.local.domain.exception.ConcurrentStateException e) {
            // A concurrent REST/MQTT transaction won the optimistic lock; the
            // loser's stale transition must not be retried (QoS-1 redelivery
            // would replay a now-stale state). Ack by returning normally.
            org.slf4j.LoggerFactory.getLogger(GameSessionListener.class)
                    .warn("Concurrent game-state modification on topic {}; dropping message (another tx won): {}", topic, e.getMessage());
        }
    }
}
