package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.ports.in.UpsertGameDefinitionRequestedUseCase;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.AdminRequestDto;
import com.gameplatform.shared.dto.GameDefinitionUpsertRequestedEventDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * Implementation of the W9 use case (PIANO §7.B): a GAME_ADMIN upserts a
 * game definition. Pre-controls the {@code GAME_ADMIN} role on
 * {@code replicated_users}, then atomically writes a
 * {@code admin_requests_local} PENDING row and the matching outbox
 * {@code GAME_DEFINITION_UPSERT_REQUESTED} event.
 */
@Service
public class UpsertGameDefinitionRequestedService
        implements UpsertGameDefinitionRequestedUseCase {

    static final String EVENT_TYPE = "GAME_DEFINITION_UPSERT_REQUESTED";
    static final String REQUIRED_ROLE = "GAME_ADMIN";

    private final UserRepository userRepository;
    private final AdminRequestOutboxWriter outboxWriter;
    private final Clock clock;

    public UpsertGameDefinitionRequestedService(UserRepository userRepository,
                                                  AdminRequestOutboxWriter outboxWriter,
                                                  Clock clock) {
        this.userRepository = userRepository;
        this.outboxWriter = outboxWriter;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AdminRequestDto upsert(GameType gameType,
                                   String name,
                                   int minPlayers,
                                   int maxPlayers,
                                   boolean teamAllowed,
                                   Map<String, Object> registrationRules,
                                   String actingUserId,
                                   String actingRole,
                                   String buildingId) {
        RolePreCheck.requireRole(userRepository, actingUserId, REQUIRED_ROLE);
        if (gameType == null) {
            throw new IllegalArgumentException("gameType cannot be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        if (minPlayers < 1 || maxPlayers < 1 || minPlayers > maxPlayers) {
            throw new IllegalArgumentException("invalid player count range");
        }
        Instant now = Instant.now(clock);
        GameDefinitionUpsertRequestedEventDto payload = new GameDefinitionUpsertRequestedEventDto(
                null, EVENT_TYPE, null, actingUserId, REQUIRED_ROLE, buildingId,
                gameType, name, minPlayers, maxPlayers, teamAllowed, registrationRules, now
        );
        return outboxWriter.writePendingRequest(EVENT_TYPE, actingUserId, REQUIRED_ROLE, buildingId, payload);
    }
}