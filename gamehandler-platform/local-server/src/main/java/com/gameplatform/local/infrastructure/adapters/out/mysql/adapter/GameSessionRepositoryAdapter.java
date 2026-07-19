package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.model.OutboxEventStatus;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.GameSessionJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.GameSessionMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.GameSessionJpaRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.OutboxEventJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.OutboxEventJpaRepository;
import java.util.Objects;
import java.util.Set;

/**
 * Adapter JPA per il port {@link GameSessionRepository}.
 * Gestisce la persistenza delle sessioni di gioco, inclusa la logica
 * di sincronizzazione che confronta le sessioni completate con gli
 * eventi della outbox per determinare quali sessioni necessitano
 * ancora di sincronizzazione verso il server centrale.
 *
 * @see GameSessionRepository
 * @see GameSessionJpaRepository
 * @see OutboxEventJpaRepository
 */
@Component
public class GameSessionRepositoryAdapter implements GameSessionRepository {

    private final GameSessionJpaRepository jpaRepository;
    private final GameSessionMapper mapper;
    private final OutboxEventJpaRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Costruisce un nuovo adapter con le dipendenze necessarie.
     *
     * @param jpaRepository       repository JPA per le sessioni di gioco
     * @param mapper              mapper per la conversione tra entity e dominio
     * @param outboxEventRepository repository JPA per gli eventi della outbox
     * @param objectMapper        mapper JSON per la lettura del payload degli eventi
     */
    public GameSessionRepositoryAdapter(GameSessionJpaRepository jpaRepository, GameSessionMapper mapper, OutboxEventJpaRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Salva una sessione di gioco nel database con flush immediato.
     * In caso di conflitto di versione, lancia una {@link ConcurrentStateException}.
     *
     * @param session la sessione di gioco da salvare
     * @return la sessione di gioco persistita
     * @throws com.gameplatform.local.domain.exception.ConcurrentStateException in caso di modifica concorrente
     */
    @Override
    public GameSession save(GameSession session) {
        GameSessionJpaEntity entity = mapper.toEntity(session);
        try {
            GameSessionJpaEntity saved = jpaRepository.saveAndFlush(entity);
            return mapper.toDomain(saved);
        } catch (org.springframework.dao.OptimisticLockingFailureException ex) {
            throw new com.gameplatform.local.domain.exception.ConcurrentStateException(
                "Concurrent modification of game session " + session.getId().value(), ex);
        }
    }

    /**
     * Recupera una sessione di gioco tramite il suo identificativo.
     *
     * @param id l'identificativo della sessione di gioco
     * @return un {@code Optional} contenente la sessione, vuoto se non trovata
     */
    @Override
    public Optional<GameSession> findById(GameSessionId id) {
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    /**
     * Recupera tutte le sessioni di gioco per un dato edificio.
     *
     * @param buildingId l'identificativo dell'edificio
     * @return una lista di sessioni di gioco per l'edificio specificato
     */
    @Override
    public List<GameSession> findByBuildingId(BuildingId buildingId) {
        return jpaRepository.findByBuildingId(buildingId.id()).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    /**
     * Recupera tutte le sessioni di gioco per un dato tipo di gioco.
     *
     * @param gameType il tipo di gioco
     * @return una lista di sessioni di gioco per il tipo specificato
     */
    @Override
    public List<GameSession> findByGameType(GameType gameType) {
        return jpaRepository.findByGameType(gameType.name()).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    /**
     * Recupera tutte le sessioni di gioco con un dato stato.
     *
     * @param status lo stato della sessione di gioco
     * @return una lista di sessioni con lo stato specificato
     */
    @Override
    public List<GameSession> findByStatus(GameStatus status) {
        return jpaRepository.findByStatus(status.name()).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    /**
     * Recupera le sessioni di gioco completate o abortite che non hanno ancora
     * un corrispondente evento di completamento inviato nella outbox.
     *
     * @return una lista di sessioni in attesa di sincronizzazione
     */
    @Override
    public List<GameSession> findPendingSync() {
        List<GameSessionJpaEntity> completedOrAbortedSessions = jpaRepository.findByStatusIn(
                List.of(GameStatus.COMPLETED.name(), GameStatus.ABORTED.name()));
        List<OutboxEventJpaEntity> sentEvents = outboxEventRepository.findByEventTypeAndStatus(
                com.gameplatform.shared.domain.events.GameSessionCompletedEvent.EVENT_TYPE,
                OutboxEventStatus.SENT.name());
        
        Set<String> sentSessionIds = sentEvents.stream()
            .map(event -> {
                try {
                    JsonNode node = objectMapper.readTree(event.getPayload());
                    JsonNode sessionIdNode = node.get("sessionId");
                    return sessionIdNode != null ? sessionIdNode.asText() : null;
                } catch (Exception e) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        return completedOrAbortedSessions.stream()
            .filter(session -> !sentSessionIds.contains(session.getId()))
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    /**
     * Recupera una sessione attiva per un dato gioco tra quelle in stato
     * WAITING, IN_PROGRESS o PAUSED.
     *
     * @param gameId l'identificativo del gioco
     * @return un {@code Optional} contenente la sessione attiva, vuoto se non trovata
     */
    @Override
    public Optional<GameSession> findActiveByGameId(GameId gameId) {
        return jpaRepository.findFirstByGameIdAndStatusIn(
            gameId.id(),
            List.of(GameStatus.WAITING.name(), GameStatus.IN_PROGRESS.name(), GameStatus.PAUSED.name())
        ).map(mapper::toDomain);
    }

    /**
     * Recupera tutte le sessioni di gioco a cui un dato utente partecipa.
     *
     * @param userId l'identificativo dell'utente partecipante
     * @return una lista di sessioni di cui l'utente è partecipante, vuota se l'utente è {@code null}
     */
    @Override
    public List<GameSession> findByParticipant(UserId userId) {
        if (userId == null) {
            return List.of();
        }
        return jpaRepository.findByParticipantUserId(userId.value()).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }
}
