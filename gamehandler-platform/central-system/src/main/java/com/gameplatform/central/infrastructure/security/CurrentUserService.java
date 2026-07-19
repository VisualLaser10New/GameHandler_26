package com.gameplatform.central.infrastructure.security;

import com.gameplatform.central.domain.model.User;
import com.gameplatform.central.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.UserId;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Service che recupera l'utente correntemente autenticato dal contesto di
 * sicurezza di Spring Security e ne espone l'identità e i ruoli.
 *
 * <p>Si appoggia a {@link UserRepository} per risolvere l'identificativo
 * dell'utente a partire dal nome utente presente nel principale di
 * autenticazione.</p>
 *
 * @see UserRepository
 * @see JwtAuthenticationFilter
 */
@Component
public class CurrentUserService {

    private final UserRepository userRepository;

    /**
     * Costruisce un {@code CurrentUserService} con il repository utenti
     * necessario per la risoluzione dell'identità.
     *
     * @param userRepository il repository per la ricerca degli utenti,
     *                       non nullo
     */
    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Restituisce l'identificativo dell'utente correntemente autenticato.
     *
     * @return un {@link Optional} contenente l'{@link UserId} dell'utente
     *         autenticato, oppure {@link Optional#empty()} se non è presente
     *         alcuna autenticazione valida o se il nome utente non corrisponde
     *         a un utente noto
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
        return userRepository.findByUsername(username).map(User::getId);
    }

    /**
     * Verifica se l'utente correntemente autenticato possiede un determinato
     * ruolo.
     *
     * @param role il nome del ruolo da verificare, con o senza il prefisso
     *             {@code ROLE_} (es. {@code "PLATFORM_ADMIN"} o
     *             {@code "ROLE_PLATFORM_ADMIN"})
     * @return {@code true} se l'utente autenticato possiede l'autorità
     *         corrispondente al ruolo specificato, {@code false} altrimenti
     *         o se non è presente alcuna autenticazione
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