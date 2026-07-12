package com.gameplatform.central.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.exception.InvalidGameDefinitionException;
import com.gameplatform.central.domain.model.GameDefinition;
import com.gameplatform.central.domain.model.OutboxEvent;
import com.gameplatform.central.domain.model.OutboxEventStatus;
import com.gameplatform.central.domain.ports.in.ListGameDefinitionsUseCase;
import com.gameplatform.central.domain.ports.in.UpsertGameDefinitionUseCase;
import com.gameplatform.central.domain.ports.out.GameDefinitionRepository;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.GameDefinitionEventDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for the central Source-of-Truth management of
 * {@link GameDefinition} entries and their replication to every active Local
 * Server via the outbox event {@code GAME_DEFINITION_UPSERTED}.
 *
 * <p>Implements {@link UpsertGameDefinitionUseCase} (upsert) and
 * {@link ListGameDefinitionsUseCase} (query). The upsert is idempotent on the
 * {@code gameType} primary key: an upsert of an already-known game type updates
 * the existing row in place (preserving {@code createdAt}) and emits a fresh
 * outbox event, mirroring the {@code UserService.saveUserOnDB} /
 * {@code LocalAdminBuildingService} pattern. The repository save and the outbox
 * save commit atomically inside the class-level transaction.</p>
 *
 * <p>The outbox event id and the {@link GameDefinitionEventDto#eventId()} share
 * one UUID so the local side can dedupe and the central
 * {@code replication_progress} bookkeeping (which always tracks the outbox
 * event id) is consistent across replication flows.</p>
 */
@Service
@Transactional
public class GameDefinitionService implements UpsertGameDefinitionUseCase, ListGameDefinitionsUseCase {

    private static final String EVENT_TYPE = "GAME_DEFINITION_UPSERTED";

    private final GameDefinitionRepository gameDefinitionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public GameDefinitionService(GameDefinitionRepository gameDefinitionRepository,
                                 OutboxEventRepository outboxEventRepository,
                                 ObjectMapper objectMapper,
                                 Clock clock) {
        this.gameDefinitionRepository = gameDefinitionRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public GameDefinition upsert(GameDefinition input) {
        if (input == null) {
            throw new InvalidGameDefinitionException("GameDefinition cannot be null");
        }
        // The GameDefinition constructor already enforces every field invariant
        // (gameType != null, name non-blank, player-count bounds, timestamps
        // non-null), so re-validating the fields here would be redundant.

        Instant now = Instant.now(clock);
        Instant createdAt = gameDefinitionRepository.existsByGameType(input.getGameType())
                ? gameDefinitionRepository.findByGameType(input.getGameType())
                        .map(GameDefinition::getCreatedAt)
                        .orElse(now)
                : now;

        GameDefinition rebuilt = new GameDefinition(
                input.getGameType(),
                input.getName(),
                input.getMinPlayers(),
                input.getMaxPlayers(),
                input.isTeamAllowed(),
                input.getRegistrationRules(),
                createdAt,
                now);

        GameDefinition saved = gameDefinitionRepository.save(rebuilt);
        writeOutboxEvent(saved);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<GameDefinition> findAll() {
        List<GameDefinition> result = gameDefinitionRepository.findAll();
        return result != null ? result : List.of();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GameDefinition> findByGameType(GameType gameType) {
        return gameDefinitionRepository.findByGameType(gameType);
    }

    /**
     * Serialises a {@link GameDefinitionEventDto} and writes it to the outbox.
     * Mirrors {@code LocalAdminBuildingService.writeOutboxEvent}: a single UUID
     * is shared by the outbox event id and the DTO {@code eventId} so the local
     * side can dedupe and the central {@code replication_progress} (which always
     * tracks the outbox event id) is consistent across flows.
     */
    private void writeOutboxEvent(GameDefinition saved) {
        String eventId = UUID.randomUUID().toString();
        GameDefinitionEventDto dto = new GameDefinitionEventDto(
                eventId,
                EVENT_TYPE,
                saved.getGameType(),
                saved.getName(),
                saved.getMinPlayers(),
                saved.getMaxPlayers(),
                saved.isTeamAllowed(),
                saved.getRegistrationRules(),
                saved.getUpdatedAt());

        String payload;
        try {
            payload = objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize GameDefinitionEventDto", e);
        }

        OutboxEvent event = new OutboxEvent(
                eventId, EVENT_TYPE, payload, OutboxEventStatus.PENDING, Instant.now(clock), null);
        outboxEventRepository.save(event);
    }
}