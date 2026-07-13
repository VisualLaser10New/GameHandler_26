package com.gameplatform.local.infrastructure.security;

import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.domain.ports.out.LocalAdminBuildingLocalRepository;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * A3 — building enforcement WITHOUT a JWT {@code buildings} claim. The local
 * {@code JwtAuthenticationFilter} populates the Spring {@link Authentication}
 * principal with the username (not the userId); this manager resolves the
 * userId via {@link UserRepository#findByUsername} and checks the replicated
 * {@code local_admin_buildings_local} table to decide whether the authenticated
 * LOCAL_ADMIN is bound to this server's building ({@code app.building-id}).
 *
 * <p>Deliberately offline-capable: the binding is read from the locally
 * replicated table (kept in sync by {@code LocalAdminBuildingSyncService}
 * consuming the Central outbox), so authorization works even when the Central
 * is unreachable.
 */
@Component
public class LocalAdminBuildingAuthorizationManager {

    private final LocalAdminBuildingLocalRepository localAdminBuildingLocalRepository;
    private final UserRepository userRepository;
    private final String appBuildingId;

    public LocalAdminBuildingAuthorizationManager(LocalAdminBuildingLocalRepository localAdminBuildingLocalRepository,
                                                  UserRepository userRepository,
                                                  @Value("${app.building-id}") String appBuildingId) {
        this.localAdminBuildingLocalRepository = localAdminBuildingLocalRepository;
        this.userRepository = userRepository;
        this.appBuildingId = appBuildingId;
    }

    public boolean canManageBuilding(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (authentication.getAuthorities() != null) {
            for (GrantedAuthority ga : authentication.getAuthorities()) {
                if ("ROLE_PLATFORM_ADMIN".equals(ga.getAuthority())) {
                    return true;
                }
            }
        }
        Object principal = authentication.getPrincipal();
        String username = principal instanceof UserDetails ud ? ud.getUsername()
                : (principal != null ? principal.toString() : null);
        if (username == null || username.isBlank()) {
            return false;
        }
        Optional<User> user = userRepository.findByUsername(username);
        if (user.isEmpty()) {
            return false;
        }
        return localAdminBuildingLocalRepository.existsByUserIdAndBuildingId(
                user.get().getUserId(), new BuildingId(appBuildingId));
    }
}