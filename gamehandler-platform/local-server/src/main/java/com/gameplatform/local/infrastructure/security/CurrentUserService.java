package com.gameplatform.local.infrastructure.security;

import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.UserId;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Servizio che risolve l'identificativo dell'utente autenticato a partire dal
 * contesto di sicurezza di Spring Security. Estrae il principale
 * {@link Authentication} e recupera lo {@link UserId} corrispondente tramite
 * la replica locale della tabella degli utenti.
 *
 * <p>Il filtro {@link JwtAuthenticationFilter} popola il principale
 * dell'{@link Authentication} con il <em>subject</em> del JWT (il nome
 * utente); questo servizio converte tale nome utente nell'{@link UserId}
 * associato consultando il repository locale.</p>
 *
 * @see JwtAuthenticationFilter
 * @see UserRepository
 */
@Component
public class CurrentUserService {

    private final UserRepository userRepository;

    /**
     * Costruisce un nuovo servizio con il repository utenti fornito.
     *
     * @param userRepository repository per la ricerca degli utenti replicati
     *                       localmente
     */
    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Restituisce l'identificativo dell'utente correntemente autenticato.
     *
     * <p>Recupera l'{@link Authentication} dal {@link SecurityContextHolder},
     * estrae il nome utente dal principale e lo risolve tramite
     * {@link UserRepository#findByUsername}.</p>
     *
     * @return l'{@link UserId} dell'utente autenticato, oppure
     *         {@link Optional#empty()} se non è presente un principale
     *         autenticato o se il nome utente non è ancora stato replicato
     *         localmente
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
     * Verifica se l'utente autenticato possiede un determinato ruolo.
     *
     * <p>Il parametro {@code role} può essere fornito senza il prefisso
     * {@code ROLE_}; il metodo lo aggiunge automaticamente prima di
     * effettuare il confronto con le authorities del principale
     * autenticato.</p>
     *
     * @param role il nome del ruolo da verificare (con o senza il prefisso
     *             {@code ROLE_}), ad esempio {@code "PLATFORM_ADMIN"}
     * @return {@code true} se il principale autenticato possiede
     *         l'autorità {@code ROLE_<role>}, {@code false} altrimenti
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