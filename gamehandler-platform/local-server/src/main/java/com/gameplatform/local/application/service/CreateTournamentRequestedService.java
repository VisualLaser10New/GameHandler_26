package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.ports.in.CreateTournamentRequestedUseCase;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.AdminRequestDto;
import com.gameplatform.shared.dto.TournamentCreateRequestedEventDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Implementation of the W12a use case (PIANO §7.B): a PLATFORM_ADMIN
 * creates a new tournament. Pre-controls the {@code PLATFORM_ADMIN}
 * role on {@code replicated_users}, then atomically writes a
 * {@code admin_requests_local} PENDING row and the matching outbox
 * {@code TOURNAMENT_CREATE_REQUESTED} event.
 */
@Service
public class CreateTournamentRequestedService implements CreateTournamentRequestedUseCase {

    static final String EVENT_TYPE = "TOURNAMENT_CREATE_REQUESTED";
    static final String REQUIRED_ROLE = "PLATFORM_ADMIN";

    private final UserRepository userRepository;
    private final AdminRequestOutboxWriter outboxWriter;
    private final Clock clock;

    public CreateTournamentRequestedService(UserRepository userRepository,
                                             AdminRequestOutboxWriter outboxWriter,
                                             Clock clock) {
        this.userRepository = userRepository;
        this.outboxWriter = outboxWriter;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AdminRequestDto create(String name,
                                   GameType gameType,
                                   boolean teamBased,
                                   int teamSize,
                                   Instant startsAt,
                                   List<String> buildingIds,
                                   String actingUserId,
                                   String actingRole,
                                   String buildingId) {
        RolePreCheck.requireRole(userRepository, actingUserId, REQUIRED_ROLE);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        if (gameType == null) {
            throw new IllegalArgumentException("gameType cannot be null");
        }
        if (startsAt == null) {
            throw new IllegalArgumentException("startsAt cannot be null");
        }
        if (buildingIds == null || buildingIds.size() < 2) {
            throw new IllegalArgumentException("buildingIds must contain at least 2 entries");
        }
        Instant now = Instant.now(clock);
        TournamentCreateRequestedEventDto payload = new TournamentCreateRequestedEventDto(
                null, EVENT_TYPE, null, actingUserId, REQUIRED_ROLE, buildingId,
                name, gameType, teamBased, teamSize, startsAt, buildingIds, now
        );
        return outboxWriter.writePendingRequest(EVENT_TYPE, actingUserId, REQUIRED_ROLE, buildingId, payload);
    }
}