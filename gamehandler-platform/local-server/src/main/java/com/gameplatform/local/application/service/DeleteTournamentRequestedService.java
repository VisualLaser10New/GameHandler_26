package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.TournamentSummaryLocal;
import com.gameplatform.local.domain.ports.in.DeleteTournamentRequestedUseCase;
import com.gameplatform.local.domain.ports.out.TournamentSummaryLocalRepository;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.dto.AdminRequestDto;
import com.gameplatform.shared.dto.TournamentDeleteRequestedEventDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Implementation of the W12f use case (PIANO §7.B): a PLATFORM_ADMIN
 * deletes a tournament. Pre-controls the {@code PLATFORM_ADMIN} role on
 * {@code replicated_users} and the DRAFT invariant on
 * {@code tournaments_summary_local} (refuses immediately with a
 * {@code FAILED} admin-request, WITHOUT writing the outbox row, when the
 * tournament is missing or not in {@code DRAFT} status), then atomically
 * writes a {@code admin_requests_local} PENDING row and the matching
 * outbox {@code TOURNAMENT_DELETE_REQUESTED} event.
 */
@Service
public class DeleteTournamentRequestedService implements DeleteTournamentRequestedUseCase {

    static final String EVENT_TYPE = "TOURNAMENT_DELETE_REQUESTED";
    static final String REQUIRED_ROLE = "PLATFORM_ADMIN";

    private final UserRepository userRepository;
    private final TournamentSummaryLocalRepository tournamentSummaryLocalRepository;
    private final AdminRequestOutboxWriter outboxWriter;
    private final Clock clock;

    public DeleteTournamentRequestedService(UserRepository userRepository,
                                             TournamentSummaryLocalRepository tournamentSummaryLocalRepository,
                                             AdminRequestOutboxWriter outboxWriter,
                                             Clock clock) {
        this.userRepository = userRepository;
        this.tournamentSummaryLocalRepository = tournamentSummaryLocalRepository;
        this.outboxWriter = outboxWriter;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AdminRequestDto delete(String tournamentId,
                                    String actingUserId,
                                    String actingRole,
                                    String buildingId) {
        RolePreCheck.requireRole(userRepository, actingUserId, REQUIRED_ROLE);
        if (tournamentId == null || tournamentId.isBlank()) {
            throw new IllegalArgumentException("tournamentId cannot be blank");
        }
        // DRAFT pre-check on tournaments_summary_local: refuse immediately
        // FAILED without outbox if the tournament is missing or not DRAFT.
        Optional<TournamentSummaryLocal> summary = tournamentSummaryLocalRepository.findById(new TournamentId(tournamentId));
        if (summary.isEmpty() || summary.get().getStatus() != TournamentStatus.DRAFT) {
            String reason = summary.isEmpty()
                    ? "{\"reason\":\"NOT_FOUND\"}"
                    : "{\"reason\":\"NOT_DRAFT\",\"status\":\"" + summary.get().getStatus() + "\"}";
            TournamentDeleteRequestedEventDto payload = new TournamentDeleteRequestedEventDto(
                    null, EVENT_TYPE, null, actingUserId, REQUIRED_ROLE, buildingId,
                    tournamentId, Instant.now(clock)
            );
            return outboxWriter.writeFailedRequest(EVENT_TYPE, actingUserId, REQUIRED_ROLE, buildingId, payload, reason);
        }
        Instant now = Instant.now(clock);
        TournamentDeleteRequestedEventDto payload = new TournamentDeleteRequestedEventDto(
                null, EVENT_TYPE, null, actingUserId, REQUIRED_ROLE, buildingId,
                tournamentId, now
        );
        return outboxWriter.writePendingRequest(EVENT_TYPE, actingUserId, REQUIRED_ROLE, buildingId, payload);
    }
}