package com.gameplatform.local.infrastructure.security;

import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.UserId;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves the authenticated principal's {@link UserId} from the Spring Security
 * context on the Local server (FASE 3, PIANO &sect;2.5 &mdash; {@code GET /api/players/me/statistics}).
 *
 * <p>Mirrors the Central {@code CurrentUserService} and the established
 * {@code LocalAdminBuildingAuthorizationManager} pattern (A3): the Local
 * {@code JwtAuthenticationFilter} populates the {@link Authentication} principal
 * with the JWT <em>subject</em> (the username), so the {@code userId} is
 * recovered by resolving the username through the locally replicated
 * {@code replicated_users} table via {@link UserRepository#findByUsername}. This
 * is a security {@code @Component} (not a REST adapter), so it may depend on
 * the {@code ports/out} {@link UserRepository}.</p>
 */
@Component
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * @return the authenticated user's id, or {@link Optional#empty()} if no
     *         authenticated principal is present or the username is not yet
     *         replicated locally (offline-first: the player simply has no local
     *         statistics yet in that case)
     */
    public Optional<UserId> getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        String username = authentication.getName();
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByUsername(username).map(User::getUserId);
    }

    /**
     * @return {@code true} iff the authenticated principal carries the
     *         {@code ROLE_<role>} authority (the {@code role} argument is given
     *         without the {@code ROLE_} prefix, e.g. {@code "PLATFORM_ADMIN"})
     */
    public boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        String authority = (role != null && role.startsWith("ROLE_")) ? role : "ROLE_" + role;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> authority.equals(a.getAuthority()));
    }
}