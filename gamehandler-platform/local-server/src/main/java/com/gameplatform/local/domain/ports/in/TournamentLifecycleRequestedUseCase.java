package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.dto.AdminRequestDto;

/**
 * Parametric use case W12b/c/d (PIANO §7.B): a PLATFORM_ADMIN opens
 * registrations, cancels, or schedules matches for a tournament. The
 * lifecycle action is discriminated by {@code eventType}, one of
 * {@code TOURNAMENT_OPEN_REQUESTED}, {@code TOURNAMENT_CANCEL_REQUESTED},
 * {@code TOURNAMENT_SCHEDULE_REQUESTED}. Pre-controls the
 * {@code PLATFORM_ADMIN} role on {@code replicated_users}, then
 * atomically writes a {@code admin_requests_local} PENDING row and the
 * matching outbox lifecycle event.
 */
public interface TournamentLifecycleRequestedUseCase {

    AdminRequestDto lifecycle(String eventType,
                               String tournamentId,
                               String actingUserId,
                               String actingRole,
                               String buildingId);
}