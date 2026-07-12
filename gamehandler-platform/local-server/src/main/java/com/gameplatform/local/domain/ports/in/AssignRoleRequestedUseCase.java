package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.dto.AdminRequestDto;

import java.util.List;

/**
 * Use case W10 (PIANO §7.B, RF-UT-02): a PLATFORM_ADMIN assigns the
 * given roles to a target user (replacing the existing role set). Pre-
 * controls the {@code PLATFORM_ADMIN} role on {@code replicated_users},
 * then atomically writes a {@code admin_requests_local} PENDING row
 * and the matching outbox {@code ROLE_ASSIGNMENT_REQUESTED} event.
 */
public interface AssignRoleRequestedUseCase {

    AdminRequestDto assign(String targetUserId,
                            List<String> roles,
                            String actingUserId,
                            String actingRole,
                            String buildingId);
}