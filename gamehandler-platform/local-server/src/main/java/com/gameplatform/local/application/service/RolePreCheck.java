package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.UserId;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

/**
 * Helper di pre-controllo del ruolo per i casi d'uso W (PIANO §7.B).
 * Oltre all'annotazione {@code @PreAuthorize} di Spring Security al
 * confine REST, questo helper verifica il ruolo sulla tabella replicata
 * localmente {@code replicated_users} in modo che un JWT scaduto (es.
 * ruolo revocato dal Central) non possa essere usato per emettere un
 * evento outbox asincrono {@code *_REQUESTED}. Lancia
 * {@link AccessDeniedException} (→ 403) in caso di ruolo non corrispondente
 * e {@link IllegalArgumentException} (→ 400) se l'utente non e' replicato
 * localmente o {@code actingUserId} e' blank.
 */
final class RolePreCheck {

    /**
     * Costruttore privato per impedire l'istanziazione della classe
     * utility {@code RolePreCheck}.
     */
    private RolePreCheck() {
    }

    /**
     * Verifica che l'utente agente esista nella replica locale e possieda
     * il ruolo richiesto.
     *
     * @param userRepository il repository degli utenti
     * @param actingUserId   l'identificativo dell'utente agente (non blank)
     * @param requiredRole   il ruolo richiesto per l'operazione
     * @return l'utente verificato
     * @throws IllegalArgumentException se actingUserId e' blank o l'utente non e' replicato localmente
     * @throws AccessDeniedException se l'utente non possiede il ruolo richiesto
     */
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