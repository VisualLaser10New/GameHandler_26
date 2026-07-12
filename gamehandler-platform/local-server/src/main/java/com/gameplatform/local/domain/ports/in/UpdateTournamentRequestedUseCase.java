package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.dto.AdminRequestDto;

import java.time.Instant;
import java.util.List;

/**
 * Use case W12e (PIANO §7.B): a PLATFORM_ADMIN updates a tournament's
 * metadata (name, startsAt, buildingIds). Pre-controls the
 * {@code PLATFORM_ADMIN} role on {@code replicated_users} and the DRAFT
 * invariant on {@code tournaments_summary_local} (refuses immediately
 * with {@code FAILED} — without writing the outbox row — when the
 * tournament is not in {@code DRAFT} status), then atomically writes a
 * {@code admin_requests_local} PENDING row and the matching outbox
 * {@code TOURNAMENT_UPDATE_REQUESTED} event.
 */
public interface UpdateTournamentRequestedUseCase {

    AdminRequestDto update(String tournamentId,
                            String name,
                            Instant startsAt,
                            List<String> buildingIds,
                            String actingUserId,
                            String actingRole,
                            String buildingId);
}