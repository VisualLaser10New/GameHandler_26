package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.ports.in.TournamentLifecycleRequestedUseCase;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.dto.AdminRequestDto;
import com.gameplatform.shared.dto.TournamentLifecycleRequestedEventDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

/**
 * Parametric implementation of the W12b/c/d use cases (PIANO §7.B). A
 * single service handles all three lifecycle transitions: OPEN,
 * CANCEL, SCHEDULE. Pre-controls the {@code PLATFORM_ADMIN} role on
 * {@code replicated_users}, then atomically writes a
 * {@code admin_requests_local} PENDING row and the matching outbox
 * parametric {@code eventType} event.
 */
@Service
public class TournamentLifecycleRequestedService implements TournamentLifecycleRequestedUseCase {

    static final String REQUIRED_ROLE = "PLATFORM_ADMIN";

    static final String OPEN_EVENT_TYPE = "TOURNAMENT_OPEN_REQUESTED";
    static final String CANCEL_EVENT_TYPE = "TOURNAMENT_CANCEL_REQUESTED";
    static final String SCHEDULE_EVENT_TYPE = "TOURNAMENT_SCHEDULE_REQUESTED";
    static final Set<String> ALLOWED_EVENT_TYPES = Set.of(OPEN_EVENT_TYPE, CANCEL_EVENT_TYPE, SCHEDULE_EVENT_TYPE);

    private final UserRepository userRepository;
    private final AdminRequestOutboxWriter outboxWriter;
    private final Clock clock;

    public TournamentLifecycleRequestedService(UserRepository userRepository,
                                                 AdminRequestOutboxWriter outboxWriter,
                                                 Clock clock) {
        this.userRepository = userRepository;
        this.outboxWriter = outboxWriter;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AdminRequestDto lifecycle(String eventType,
                                      String tournamentId,
                                      String actingUserId,
                                      String actingRole,
                                      String buildingId) {
        if (eventType == null || !ALLOWED_EVENT_TYPES.contains(eventType)) {
            throw new IllegalArgumentException(
                    "Unsupported lifecycle eventType: " + eventType
                    + " (expected one of " + ALLOWED_EVENT_TYPES + ")");
        }
        RolePreCheck.requireRole(userRepository, actingUserId, REQUIRED_ROLE);
        if (tournamentId == null || tournamentId.isBlank()) {
            throw new IllegalArgumentException("tournamentId cannot be blank");
        }
        Instant now = Instant.now(clock);
        TournamentLifecycleRequestedEventDto payload = new TournamentLifecycleRequestedEventDto(
                null, eventType, null, actingUserId, REQUIRED_ROLE, buildingId,
                tournamentId, now
        );
        return outboxWriter.writePendingRequest(eventType, actingUserId, REQUIRED_ROLE, buildingId, payload);
    }
}