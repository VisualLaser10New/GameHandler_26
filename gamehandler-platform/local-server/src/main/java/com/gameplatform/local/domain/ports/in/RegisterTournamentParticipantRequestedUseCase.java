package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.dto.AdminRequestDto;

/**
 * Use case W6 (PIANO §7.B): a PLAYER registers as a tournament
 * participant (individual or team captain). Pre-controls the
 * {@code PLAYER} role on {@code replicated_users}, then atomically
 * writes a {@code admin_requests_local} PENDING row and the matching
 * outbox {@code PARTICIPANT_REGISTER_REQUESTED} event (the
 * {@code requestId} equals the {@code eventId}). Returns the persisted
 * admin-request projection (status = {@code PENDING}) so the client can
 * poll {@code GET /api/admin/requests/{requestId}} for the eventual
 * COMPLETED / FAILED state.
 */
public interface RegisterTournamentParticipantRequestedUseCase {

    AdminRequestDto register(String tournamentId,
                              String actingUserId,
                              String actingRole,
                              String buildingId,
                              String teamName,
                              java.util.List<String> teamMemberIds);
}