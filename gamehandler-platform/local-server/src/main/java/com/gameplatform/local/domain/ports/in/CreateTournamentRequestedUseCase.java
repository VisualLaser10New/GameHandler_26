package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.AdminRequestDto;

import java.time.Instant;
import java.util.List;

/**
 * Use case W12a (PIANO §7.B): a PLATFORM_ADMIN creates a new
 * tournament. Pre-controls the {@code PLATFORM_ADMIN} role on
 * {@code replicated_users}, then atomically writes a
 * {@code admin_requests_local} PENDING row and the matching outbox
 * {@code TOURNAMENT_CREATE_REQUESTED} event.
 */
public interface CreateTournamentRequestedUseCase {

    AdminRequestDto create(String name,
                            GameType gameType,
                            boolean teamBased,
                            int teamSize,
                            Instant startsAt,
                            List<String> buildingIds,
                            String actingUserId,
                            String actingRole,
                            String buildingId);
}