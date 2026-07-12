package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.ports.in.RegisterTournamentParticipantRequestedUseCase;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.dto.AdminRequestDto;
import com.gameplatform.shared.dto.ParticipantRegisterRequestedEventDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Implementation of the W6 use case (PIANO §7.B): a PLAYER registers as a
 * tournament participant (individual or team captain). Pre-controls the
 * {@code PLAYER} role on {@code replicated_users}, then atomically writes a
 * {@code admin_requests_local} PENDING row and the matching outbox
 * {@code PARTICIPANT_REGISTER_REQUESTED} event (the {@code requestId} equals
 * the {@code eventId}).
 */
@Service
public class RegisterTournamentParticipantRequestedService
        implements RegisterTournamentParticipantRequestedUseCase {

    static final String EVENT_TYPE = "PARTICIPANT_REGISTER_REQUESTED";
    static final String REQUIRED_ROLE = "PLAYER";

    private final UserRepository userRepository;
    private final AdminRequestOutboxWriter outboxWriter;
    private final Clock clock;

    public RegisterTournamentParticipantRequestedService(UserRepository userRepository,
                                                          AdminRequestOutboxWriter outboxWriter,
                                                          Clock clock) {
        this.userRepository = userRepository;
        this.outboxWriter = outboxWriter;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AdminRequestDto register(String tournamentId,
                                      String actingUserId,
                                      String actingRole,
                                      String buildingId,
                                      String teamName,
                                      List<String> teamMemberIds) {
        RolePreCheck.requireRole(userRepository, actingUserId, REQUIRED_ROLE);
        if (tournamentId == null || tournamentId.isBlank()) {
            throw new IllegalArgumentException("tournamentId cannot be blank");
        }
        Instant now = Instant.now(clock);
        ParticipantRegisterRequestedEventDto payload = new ParticipantRegisterRequestedEventDto(
                null, // eventId placeholder — outboxWriter replaces requestId as eventId below
                EVENT_TYPE,
                null, // requestId placeholder (filled in by writer's serialization route)
                actingUserId,
                REQUIRED_ROLE,
                buildingId,
                tournamentId,
                teamName,
                teamMemberIds,
                now
        );
        return outboxWriter.writePendingRequest(EVENT_TYPE, actingUserId, REQUIRED_ROLE, buildingId, payload);
    }
}