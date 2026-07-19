package com.gameplatform.shared.dto;

import java.time.Instant;

/**
 * Payload parametrico dell'outbox per i tre eventi di richiesta amministrativa
 * di ciclo di vita {@code TOURNAMENT_OPEN_REQUESTED}, {@code TOURNAMENT_CANCEL_REQUESTED} e
 * {@code TOURNAMENT_SCHEDULE_REQUESTED} (PIANO §7.B W12), che trasportano tutti lo stesso
 * payload {@code (tournamentId)} e si differenziano unicamente per {@code eventType}.
 * Viene consumato dal {@code SyncEventProcessor} Centrale nei rami §7.A.7, che
 * smista rispettivamente verso {@code OpenTournamentRegistrationUseCase.open},
 * {@code CancelTournamentUseCase.cancel} o
 * {@code ScheduleTournamentMatchesUseCase.schedule}.
 *
 * <p>Il {@code requestId} coincide con l'{@code eventId} dell'outbox Locale; l'evento
 * di ritorno Centrale (es. {@code TOURNAMENT_SUMMARY_UPSERTED}) lo riporta come
 * {@code originatingRequestId} affinché il nodo Locale possa invocare {@code markCompleted}.</p>
 *
 * @param eventId        l'identificativo (UUID) dell'evento dell'outbox Locale; non deve essere {@code null}
 * @param eventType      il tipo di evento richiesto, uno tra {@code TOURNAMENT_OPEN_REQUESTED},
 *                       {@code TOURNAMENT_CANCEL_REQUESTED} e
 *                       {@code TOURNAMENT_SCHEDULE_REQUESTED}; non deve essere {@code null}
 * @param requestId      l'identificativo della richiesta amministrativa (uguale a {@code eventId}); non deve essere {@code null}
 * @param actingUserId   l'identificativo dell'utente amministratore (PLATFORM_ADMIN) che richiede la modifica; non deve essere {@code null}
 * @param actingRole     il ruolo dell'amministratore che effettua l'operazione; non deve essere {@code null}
 * @param buildingId     l'identificativo della sede alla quale l'amministratore è connesso; non deve essere {@code null}
 * @param tournamentId   l'identificativo del torneo destinatario dell'operazione; non deve essere {@code null}
 * @param createdAt      l'istante di creazione della richiesta; non deve essere {@code null}
 *
 * @see com.gameplatform.shared.dto.OpenTournamentRegistrationUseCase
 * @see com.gameplatform.shared.dto.CancelTournamentUseCase
 * @see com.gameplatform.shared.dto.ScheduleTournamentMatchesUseCase
 */
public record TournamentLifecycleRequestedEventDto(
        String eventId,
        String eventType,
        String requestId,
        String actingUserId,
        String actingRole,
        String buildingId,
        String tournamentId,
        Instant createdAt
) {
}