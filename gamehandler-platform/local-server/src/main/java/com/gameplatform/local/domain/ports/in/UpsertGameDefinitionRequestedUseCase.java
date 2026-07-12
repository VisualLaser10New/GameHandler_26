package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.AdminRequestDto;

import java.util.Map;

/**
 * Use case W9 (PIANO §7.B): a GAME_ADMIN upserts (create or update) a
 * game definition. Pre-controls the {@code GAME_ADMIN} role on
 * {@code replicated_users}, then atomically writes a
 * {@code admin_requests_local} PENDING row and the matching outbox
 * {@code GAME_DEFINITION_UPSERT_REQUESTED} event.
 */
public interface UpsertGameDefinitionRequestedUseCase {

    AdminRequestDto upsert(GameType gameType,
                            String name,
                            int minPlayers,
                            int maxPlayers,
                            boolean teamAllowed,
                            Map<String, Object> registrationRules,
                            String actingUserId,
                            String actingRole,
                            String buildingId);
}