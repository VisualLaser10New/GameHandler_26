package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.TournamentSummaryLocal;
import com.gameplatform.local.domain.ports.in.DeleteTournamentRequestedUseCase;
import com.gameplatform.local.domain.ports.out.TournamentSummaryLocalRepository;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.dto.AdminRequestDto;
import com.gameplatform.shared.dto.TournamentDeleteRequestedEventDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Implementazione del caso d'uso W12f (PIANO §7.B): un PLATFORM_ADMIN
 * elimina un torneo. Esegue il pre-controllo del ruolo
 * {@code PLATFORM_ADMIN} su {@code replicated_users} e dell'invariante
 * DRAFT su {@code tournaments_summary_local} (rifiuta immediatamente con
 * FAILED senza scrivere riga outbox se il torneo non e' in stato DRAFT o
 * non esiste), poi scrive atomicamente una riga {@code admin_requests_local}
 * PENDING e l'evento outbox {@code TOURNAMENT_DELETE_REQUESTED}.
 *
 * @see DeleteTournamentRequestedUseCase
 * @see AdminRequestOutboxWriter
 * @see TournamentSummaryLocal
 */
@Service
public class DeleteTournamentRequestedService implements DeleteTournamentRequestedUseCase {

    static final String EVENT_TYPE = "TOURNAMENT_DELETE_REQUESTED";
    static final String REQUIRED_ROLE = "PLATFORM_ADMIN";

    private final UserRepository userRepository;
    private final TournamentSummaryLocalRepository tournamentSummaryLocalRepository;
    private final AdminRequestOutboxWriter outboxWriter;
    private final Clock clock;

    public DeleteTournamentRequestedService(UserRepository userRepository,
                                             TournamentSummaryLocalRepository tournamentSummaryLocalRepository,
                                             AdminRequestOutboxWriter outboxWriter,
                                             Clock clock) {
        this.userRepository = userRepository;
        this.tournamentSummaryLocalRepository = tournamentSummaryLocalRepository;
        this.outboxWriter = outboxWriter;
        this.clock = clock;
    }

    /**
     * Elimina un torneo. Verifica il ruolo PLATFORM_ADMIN e l'invariante
     * DRAFT sul torneo; se il torneo non esiste o non e' in stato DRAFT,
     * scrive una richiesta admin FAILED senza outbox, altrimenti scrive
     * la richiesta PENDING con l'evento outbox.
     *
     * @param tournamentId l'identificativo del torneo da eliminare (non blank)
     * @param actingUserId l'identificativo dell'utente richiedente
     * @param actingRole   il ruolo con cui l'utente agisce
     * @param buildingId   l'identificativo del building di competenza
     * @return il DTO della richiesta admin creata (FAILED se il pre-controllo DRAFT fallisce, PENDING altrimenti)
     * @throws IllegalArgumentException se tournamentId e' blank
     * @throws org.springframework.security.access.AccessDeniedException se l'utente non ha il ruolo PLATFORM_ADMIN
     */
    @Override
    @Transactional
    public AdminRequestDto delete(String tournamentId,
                                    String actingUserId,
                                    String actingRole,
                                    String buildingId) {
        RolePreCheck.requireRole(userRepository, actingUserId, REQUIRED_ROLE);
        if (tournamentId == null || tournamentId.isBlank()) {
            throw new IllegalArgumentException("tournamentId cannot be blank");
        }
        // DRAFT pre-check on tournaments_summary_local: refuse immediately
        // FAILED without outbox if the tournament is missing or not DRAFT.
        Optional<TournamentSummaryLocal> summary = tournamentSummaryLocalRepository.findById(new TournamentId(tournamentId));
        if (summary.isEmpty() || summary.get().getStatus() != TournamentStatus.DRAFT) {
            String reason = summary.isEmpty()
                    ? "{\"reason\":\"NOT_FOUND\"}"
                    : "{\"reason\":\"NOT_DRAFT\",\"status\":\"" + summary.get().getStatus() + "\"}";
            TournamentDeleteRequestedEventDto payload = new TournamentDeleteRequestedEventDto(
                    null, EVENT_TYPE, null, actingUserId, REQUIRED_ROLE, buildingId,
                    tournamentId, Instant.now(clock)
            );
            return outboxWriter.writeFailedRequest(EVENT_TYPE, actingUserId, REQUIRED_ROLE, buildingId, payload, reason);
        }
        Instant now = Instant.now(clock);
        TournamentDeleteRequestedEventDto payload = new TournamentDeleteRequestedEventDto(
                null, EVENT_TYPE, null, actingUserId, REQUIRED_ROLE, buildingId,
                tournamentId, now
        );
        return outboxWriter.writePendingRequest(EVENT_TYPE, actingUserId, REQUIRED_ROLE, buildingId, payload);
    }
}