package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.UserId;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

/**
 * Defense-in-depth role pre-check helper for the W use cases (PIANO §7.B).
 * The Spring Security method-level {@code @PreAuthorize} annotation already
 * enforces the role at the REST adapter boundary; this helper additionally
 * verifies the role against the locally replicated {@code replicated_users}
 * table so a stale JWT (e.g. a role revoked by the Central) cannot be used
 * to issue an async {@code *_REQUESTED} outbox event. Throws
 * {@link AccessDeniedException} (→ 403 via {@code GlobalExceptionHandler})
 * on role mismatch and {@link IllegalArgumentException} (→ 400) when the
 * user is not locally replicated or the {@code actingUserId} is blank.
 */
final class RolePreCheck {

    private RolePreCheck() {
    }

    static User requireRole(UserRepository userRepository, String actingUserId, String requiredRole) {
        if (actingUserId == null || actingUserId.isBlank()) {
            throw new IllegalArgumentException("actingUserId cannot be blank");
        }
        Optional<User> existing = userRepository.findById(new UserId(actingUserId));
        if (existing.isEmpty()) {
            throw new IllegalArgumentException(
                    "Acting user " + actingUserId + " is not replicated locally");
        }
        User user = existing.get();
        if (user.getRoles() == null || user.getRoles().stream()
                .noneMatch(r -> requiredRole.equalsIgnoreCase(r.trim()))) {
            throw new AccessDeniedException(
                    "User " + actingUserId + " does not have role " + requiredRole);
        }
        return user;
    }
}