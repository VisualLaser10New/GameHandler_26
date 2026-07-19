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
 * Implementazione del caso d'uso W10 (PIANO §7.B): un PLATFORM_ADMIN
 * assegna un nuovo set di ruoli a un utente target. Esegue il pre-controllo
 * del ruolo {@code PLATFORM_ADMIN} su {@code replicated_users}, quindi
 * scrive atomicamente una riga {@code admin_requests_local} in stato PENDING
 * e il corrispondente evento outbox {@code ROLE_ASSIGNMENT_REQUESTED}.
 *
 * @see AssignRoleRequestedUseCase
 * @see AdminRequestOutboxWriter
 * @see RolePreCheck
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

    /**
     * Assegna un nuovo set di ruoli a un utente target. Verifica che
     * l'utente agente possieda il ruolo {@code PLATFORM_ADMIN} e che i
     * parametri siano validi, poi scrive la richiesta admin PENDING e
     * l'evento outbox.
     *
     * @param targetUserId l'identificativo dell'utente a cui assegnare i ruoli (non blank)
     * @param roles        la lista dei ruoli da assegnare (non vuota)
     * @param actingUserId l'identificativo dell'utente che richiede l'operazione
     * @param actingRole   il ruolo con cui l'utente agente opera
     * @param buildingId   l'identificativo del building di competenza
     * @return il DTO della richiesta admin creata
     * @throws IllegalArgumentException se targetUserId e' blank o roles e' vuoto
     * @throws org.springframework.security.access.AccessDeniedException se l'utente agente non ha il ruolo PLATFORM_ADMIN
     */
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