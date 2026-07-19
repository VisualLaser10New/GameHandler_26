package com.gameplatform.shared.dto;

import com.gameplatform.shared.domain.model.GameType;

import java.time.Instant;
import java.util.List;

/**
 * Payload dell'outbox per l'evento {@code TOURNAMENT_CREATE_REQUESTED}, emesso da un
 * Local Server nell'ambito di un caso d'uso PLATFORM_ADMIN (PIANO §7.B W12) e
 * consumato dal {@code SyncEventProcessor} Centrale §7.A.7, che delega al
 * {@code CreateTournamentUseCase.create(tournament, buildingIds, originatingRequestId)}.
 *
 * <p>Il {@code requestId} coincide con l'{@code eventId} dell'outbox Locale; l'evento
 * di ritorno Centrale ({@code TOURNAMENT_SUMMARY_UPSERTED}) lo riporta come
 * {@code originatingRequestId} affinché il Locale possa invocare {@code markCompleted}.</p>
 *
 * @param eventId        identificativo dell'evento outbox Locale (UUID); non deve essere {@code null} né vuoto
 * @param eventType      tipo dell'evento, sempre {@code TOURNAMENT_CREATE_REQUESTED}; non deve essere {@code null} né vuoto
 * @param requestId      identificativo della richiesta dell'admin, uguale a {@code eventId}; non deve essere {@code null} né vuoto
 * @param actingUserId   identificativo dell'utente admin (PLATFORM_ADMIN) che effettua la richiesta; non deve essere {@code null} né vuoto
 * @param actingRole     ruolo dell'admin che effettua l'operazione; non deve essere {@code null} né vuoto
 * @param buildingId     edificio a cui l'admin è connesso al momento della richiesta; non deve essere {@code null} né vuoto
 * @param name           nome del torneo da creare; non deve essere {@code null} né vuoto
 * @param gameType       tipologia di gioco del torneo; non deve essere {@code null}
 * @param teamBased      {@code true} se il torneo è a squadre, {@code false} se individuale
 * @param teamSize       dimensione della squadra; vale 1 per tornei individuali, maggiore di 1 per tornei a squadre
 * @param startsAt       istante pianificato di inizio del torneo; non deve essere {@code null}
 * @param buildingIds    elenco degli edifici che ospitano il torneo; non deve essere {@code null} né vuoto
 * @param createdAt      istante di creazione della richiesta; non deve essere {@code null}
 *
 * @see com.gameplatform.shared.dto.TournamentSummaryUpsertedEventDto
 */
public record TournamentCreateRequestedEventDto(
        String eventId,
        String eventType,
        String requestId,
        String actingUserId,
        String actingRole,
        String buildingId,
        String name,
        GameType gameType,
        boolean teamBased,
        int teamSize,
        Instant startsAt,
        List<String> buildingIds,
        Instant createdAt
) {
}