package com.gameplatform.shared.dto;

import java.time.Instant;

/**
 * Payload dell'outbox per l'evento {@code TOURNAMENT_DELETE_REQUESTED}, emesso da
 * un caso d'uso {@code PLATFORM_ADMIN} del Local Server (PIANO §7.B W12) e
 * consumato dal {@code SyncEventProcessor} Central nel ramo §7.A.7
 * {@code TOURNAMENT_DELETE_REQUESTED}, che delega a
 * {@code DeleteTournamentUseCase.delete(tournamentId, originatingRequestId)}.
 *
 * <p>Il {@code requestId} coincide con l'{@code eventId} dell'outbox Local;
 * l'evento di ritorno Central ({@code TOURNAMENT_SUMMARY_UPSERTED} tombstone con
 * {@code deleted=true}) lo riporta come {@code originatingRequestId} affinché
 * il Local possa invocare {@code markCompleted}.</p>
 *
 * @param eventId        l'id dell'evento outbox Local (UUID), non deve essere {@code null}
 * @param eventType      il tipo di evento, sempre {@code TOURNAMENT_DELETE_REQUESTED}
 * @param requestId      l'id della richiesta dell'amministratore (== {@code eventId})
 * @param actingUserId   l'id dell'utente amministratore ({@code PLATFORM_ADMIN}) che richiede l'eliminazione
 * @param actingRole     il ruolo dell'amministratore che effettua l'operazione
 * @param buildingId     l'id della sala (building) in cui l'amministratore è connesso
 * @param tournamentId   l'id del torneo da eliminare
 * @param createdAt      l'istante di creazione della richiesta
 *
 * @see com.gameplatform.shared.dto.TournamentSummaryUpsertedEventDto
 */
public record TournamentDeleteRequestedEventDto(
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