package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.ports.in.TournamentLifecycleRequestedUseCase;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.dto.AdminRequestDto;
import com.gameplatform.shared.dto.TournamentLifecycleRequestedEventDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

/**
 * Implementazione parametrica dei casi d'uso W12b/c/d (PIANO §7.B).
 * Un singolo servizio gestisce tutte e tre le transizioni del ciclo
 * di vita: OPEN, CANCEL, SCHEDULE. Esegue il pre-controllo del ruolo
 * {@code PLATFORM_ADMIN} su {@code replicated_users}, poi scrive
 * atomicamente una riga {@code admin_requests_local} PENDING e l'evento
 * outbox parametrico corrispondente all'{@code eventType}.
 *
 * @see TournamentLifecycleRequestedUseCase
 * @see AdminRequestOutboxWriter
 * @see RolePreCheck
 */
@Service
public class TournamentLifecycleRequestedService implements TournamentLifecycleRequestedUseCase {

    static final String REQUIRED_ROLE = "PLATFORM_ADMIN";

    static final String OPEN_EVENT_TYPE = "TOURNAMENT_OPEN_REQUESTED";
    static final String CANCEL_EVENT_TYPE = "TOURNAMENT_CANCEL_REQUESTED";
    static final String SCHEDULE_EVENT_TYPE = "TOURNAMENT_SCHEDULE_REQUESTED";
    static final Set<String> ALLOWED_EVENT_TYPES = Set.of(OPEN_EVENT_TYPE, CANCEL_EVENT_TYPE, SCHEDULE_EVENT_TYPE);

    private final UserRepository userRepository;
    private final AdminRequestOutboxWriter outboxWriter;
    private final Clock clock;

    public TournamentLifecycleRequestedService(UserRepository userRepository,
                                                 AdminRequestOutboxWriter outboxWriter,
                                                 Clock clock) {
        this.userRepository = userRepository;
        this.outboxWriter = outboxWriter;
        this.clock = clock;
    }

    /**
     * Esegue una transizione del ciclo di vita di un torneo (OPEN, CANCEL,
     * SCHEDULE). Verifica il ruolo PLATFORM_ADMIN e la validita' dei
     * parametri, poi scrive la richiesta admin PENDING e l'evento outbox.
     *
     * @param eventType    il tipo di transizione (uno tra TOURNAMENT_OPEN_REQUESTED,
     *                     TOURNAMENT_CANCEL_REQUESTED, TOURNAMENT_SCHEDULE_REQUESTED)
     * @param tournamentId l'identificativo del torneo (non blank)
     * @param actingUserId l'identificativo dell'utente richiedente
     * @param actingRole   il ruolo con cui l'utente agisce
     * @param buildingId   l'identificativo del building di competenza
     * @return il DTO della richiesta admin creata
     * @throws IllegalArgumentException se eventType non e' supportato o tournamentId e' blank
     * @throws org.springframework.security.access.AccessDeniedException se l'utente non ha il ruolo PLATFORM_ADMIN
     */
    @Override
    @Transactional
    public AdminRequestDto lifecycle(String eventType,
                                      String tournamentId,
                                      String actingUserId,
                                      String actingRole,
                                      String buildingId) {
        if (eventType == null || !ALLOWED_EVENT_TYPES.contains(eventType)) {
            throw new IllegalArgumentException(
                    "Unsupported lifecycle eventType: " + eventType
                    + " (expected one of " + ALLOWED_EVENT_TYPES + ")");
        }
        RolePreCheck.requireRole(userRepository, actingUserId, REQUIRED_ROLE);
        if (tournamentId == null || tournamentId.isBlank()) {
            throw new IllegalArgumentException("tournamentId cannot be blank");
        }
        Instant now = Instant.now(clock);
        TournamentLifecycleRequestedEventDto payload = new TournamentLifecycleRequestedEventDto(
                null, eventType, null, actingUserId, REQUIRED_ROLE, buildingId,
                tournamentId, now
        );
        return outboxWriter.writePendingRequest(eventType, actingUserId, REQUIRED_ROLE, buildingId, payload);
    }
}