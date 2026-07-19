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
 * Gestore delle autorizzazioni per determinare se un amministratore locale
 * (LOCAL_ADMIN) ha il permesso di gestire un determinato edificio.
 *
 * <p>Risolve l'identificativo dell'utente a partire dal nome utente
 * presente nel principale {@link Authentication} tramite
 * {@link UserRepository#findByUsername}, quindi verifica l'esistenza
 * di un'associazione tra l'utente e l'edificio configurato
 * ({@code app.building-id}) nella tabella locale replicata
 * {@code local_admin_buildings_local}.</p>
 *
 * <p>Funziona anche offline: la tabella replicata viene mantenuta in
 * sincronia dal servizio {@code LocalAdminBuildingSyncService} che
 * consuma la outbox del server centrale.</p>
 *
 * @see JwtAuthenticationFilter
 * @see com.gameplatform.local.domain.ports.out.LocalAdminBuildingLocalRepository
 */
@Component
public class LocalAdminBuildingAuthorizationManager {

    private final LocalAdminBuildingLocalRepository localAdminBuildingLocalRepository;
    private final UserRepository userRepository;
    private final String appBuildingId;

    /**
     * Costruisce il gestore con i repository e l'identificativo
     * dell'edificio configurato.
     *
     * @param localAdminBuildingLocalRepository repository locale per le
     *                                          associazioni admin-edificio
     * @param userRepository                    repository per la ricerca
     *                                          degli utenti replicati
     * @param appBuildingId                     identificativo dell'edificio
     *                                          gestito da questo server
     *                                          locale
     */
    public LocalAdminBuildingAuthorizationManager(LocalAdminBuildingLocalRepository localAdminBuildingLocalRepository,
                                                  UserRepository userRepository,
                                                  @Value("${app.building-id}") String appBuildingId) {
        this.localAdminBuildingLocalRepository = localAdminBuildingLocalRepository;
        this.userRepository = userRepository;
        this.appBuildingId = appBuildingId;
    }

    /**
     * Verifica se l'utente autenticato può gestire l'edificio configurato
     * per questo server locale.
     *
     * <p>Un utente con autorità {@code ROLE_PLATFORM_ADMIN} ha accesso
     * immediato a tutti gli edifici. Per gli amministratori locali
     * (LOCAL_ADMIN), viene verificata l'esistenza di un'associazione
     * nella tabella {@code local_admin_buildings_local} tra l'utente
     * e l'edificio identificato da {@code app.building-id}.</p>
     *
     * @param authentication oggetto di autenticazione contenente il
     *                       principale e le authorities
     * @return {@code true} se l'utente può gestire l'edificio,
     *         {@code false} altrimenti
     */
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