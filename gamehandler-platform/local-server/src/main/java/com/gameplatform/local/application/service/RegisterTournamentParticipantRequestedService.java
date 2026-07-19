package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.ports.in.RegisterTournamentParticipantRequestedUseCase;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.dto.AdminRequestDto;
import com.gameplatform.shared.dto.ParticipantRegisterRequestedEventDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Implementazione del caso d'uso W6 (PIANO §7.B): un PLAYER si registra
 * come partecipante a un torneo (individuale o capitano di squadra).
 * Esegue il pre-controllo del ruolo {@code PLAYER} su {@code replicated_users},
 * poi scrive atomicamente una riga {@code admin_requests_local} PENDING e
 * l'evento outbox {@code PARTICIPANT_REGISTER_REQUESTED}.
 *
 * @see RegisterTournamentParticipantRequestedUseCase
 * @see AdminRequestOutboxWriter
 * @see RolePreCheck
 */
@Service
public class RegisterTournamentParticipantRequestedService
        implements RegisterTournamentParticipantRequestedUseCase {

    static final String EVENT_TYPE = "PARTICIPANT_REGISTER_REQUESTED";
    static final String REQUIRED_ROLE = "PLAYER";

    private final UserRepository userRepository;
    private final AdminRequestOutboxWriter outboxWriter;
    private final Clock clock;

    public RegisterTournamentParticipantRequestedService(UserRepository userRepository,
                                                          AdminRequestOutboxWriter outboxWriter,
                                                          Clock clock) {
        this.userRepository = userRepository;
        this.outboxWriter = outboxWriter;
        this.clock = clock;
    }

    /**
     * Registra un partecipante a un torneo. Verifica il ruolo PLAYER
     * e la validita' del tournamentId, poi scrive la richiesta admin
     * PENDING e l'evento outbox.
     *
     * @param tournamentId  l'identificativo del torneo (non blank)
     * @param actingUserId  l'identificativo dell'utente richiedente
     * @param actingRole    il ruolo con cui l'utente agisce
     * @param buildingId    l'identificativo del building di competenza
     * @param teamName      il nome della squadra (opzionale, per registrazione di squadra)
     * @param teamMemberIds la lista degli ID dei membri della squadra (opzionale)
     * @return il DTO della richiesta admin creata
     * @throws IllegalArgumentException se tournamentId e' blank
     * @throws org.springframework.security.access.AccessDeniedException se l'utente non ha il ruolo PLAYER
     */
    @Override
    @Transactional
    public AdminRequestDto register(String tournamentId,
                                      String actingUserId,
                                      String actingRole,
                                      String buildingId,
                                      String teamName,
                                      List<String> teamMemberIds) {
        RolePreCheck.requireRole(userRepository, actingUserId, REQUIRED_ROLE);
        if (tournamentId == null || tournamentId.isBlank()) {
            throw new IllegalArgumentException("tournamentId cannot be blank");
        }
        Instant now = Instant.now(clock);
        ParticipantRegisterRequestedEventDto payload = new ParticipantRegisterRequestedEventDto(
                null, // eventId placeholder — outboxWriter replaces requestId as eventId below
                EVENT_TYPE,
                null, // requestId placeholder (filled in by writer's serialization route)
                actingUserId,
                REQUIRED_ROLE,
                buildingId,
                tournamentId,
                teamName,
                teamMemberIds,
                now
        );
        return outboxWriter.writePendingRequest(EVENT_TYPE, actingUserId, REQUIRED_ROLE, buildingId, payload);
    }
}