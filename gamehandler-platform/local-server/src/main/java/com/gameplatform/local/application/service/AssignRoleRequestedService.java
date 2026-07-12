package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.ports.in.AssignRoleRequestedUseCase;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.dto.AdminRequestDto;
import com.gameplatform.shared.dto.RoleAssignmentRequestedEventDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Implementation of the W10 use case (PIANO §7.B, RF-UT-02): a
 * PLATFORM_ADMIN assigns a new role set to a target user. Pre-controls
 * the {@code PLATFORM_ADMIN} role on {@code replicated_users}, then
 * atomically writes a {@code admin_requests_local} PENDING row and the
 * matching outbox {@code ROLE_ASSIGNMENT_REQUESTED} event.
 */
@Service
public class AssignRoleRequestedService implements AssignRoleRequestedUseCase {

    static final String EVENT_TYPE = "ROLE_ASSIGNMENT_REQUESTED";
    static final String REQUIRED_ROLE = "PLATFORM_ADMIN";

    private final UserRepository userRepository;
    private final AdminRequestOutboxWriter outboxWriter;
    private final Clock clock;

    public AssignRoleRequestedService(UserRepository userRepository,
                                       AdminRequestOutboxWriter outboxWriter,
                                       Clock clock) {
        this.userRepository = userRepository;
        this.outboxWriter = outboxWriter;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AdminRequestDto assign(String targetUserId,
                                    List<String> roles,
                                    String actingUserId,
                                    String actingRole,
                                    String buildingId) {
        RolePreCheck.requireRole(userRepository, actingUserId, REQUIRED_ROLE);
        if (targetUserId == null || targetUserId.isBlank()) {
            throw new IllegalArgumentException("targetUserId cannot be blank");
        }
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("roles cannot be empty");
        }
        Instant now = Instant.now(clock);
        RoleAssignmentRequestedEventDto payload = new RoleAssignmentRequestedEventDto(
                null, EVENT_TYPE, null, actingUserId, REQUIRED_ROLE, buildingId,
                targetUserId, roles, now
        );
        return outboxWriter.writePendingRequest(EVENT_TYPE, actingUserId, REQUIRED_ROLE, buildingId, payload);
    }
}