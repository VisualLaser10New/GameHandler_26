package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.dto.AdminRequestDto;

/**
 * Use case W12f (PIANO §7.B): a PLATFORM_ADMIN deletes a tournament.
 * Pre-controls the {@code PLATFORM_ADMIN} role on {@code replicated_users}
 * and the DRAFT invariant on {@code tournaments_summary_local} (refuses
 * immediately with {@code FAILED} — without writing the outbox row —
 * when the tournament is not in {@code DRAFT} status), then atomically
 * writes a {@code admin_requests_local} PENDING row and the matching
 * outbox {@code TOURNAMENT_DELETE_REQUESTED} event.
 */
public interface DeleteTournamentRequestedUseCase {

    AdminRequestDto delete(String tournamentId,
                            String actingUserId,
                            String actingRole,
                            String buildingId);
}