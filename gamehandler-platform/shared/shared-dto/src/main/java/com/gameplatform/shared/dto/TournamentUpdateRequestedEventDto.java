package com.gameplatform.shared.dto;

import java.time.Instant;
import java.util.List;

/**
 * DTO di tipo record che rappresenta il payload dell'outbox per l'evento
 * {@code TOURNAMENT_UPDATE_REQUESTED}. Viene emesso da un caso d'uso
 * PLATFORM_ADMIN del Local Server (PIANO §7.B W12) e consumato dal
 * {@code SyncEventProcessor} Central (§7.A.7), che delega al
 * {@code UpdateTournamentUseCase} per applicare le modifiche richieste.
 *
 * <p>Il campo {@code requestId} coincide con l'{@code eventId} dell'outbox del
 * Local Server; l'evento di ritorno del Central ({@code TOURNAMENT_SUMMARY_UPSERTED})
 * lo riporta come {@code originatingRequestId} affinch&eacute; il Local possa
 * completare l'operazione tramite {@code markCompleted}.</p>
 *
 * @param eventId        l'identificativo (UUID) dell'evento nell'outbox del Local Server
 * @param eventType      il tipo di evento, sempre {@code TOURNAMENT_UPDATE_REQUESTED}
 * @param requestId      l'identificativo della richiesta admin (uguale a {@code eventId})
 * @param actingUserId   l'identificativo dell'utente admin (PLATFORM_ADMIN) che richiede la modifica
 * @param actingRole     il ruolo dell'utente admin che effettua l'operazione
 * @param buildingId     l'edificio presso cui l'admin &egrave; connesso
 * @param tournamentId   l'identificativo del torneo oggetto della modifica
 * @param name           il nuovo nome da assegnare al torneo
 * @param startsAt       il nuovo istante di avvio pianificato del torneo
 * @param buildingIds    gli edifici che ospitano il torneo dopo l'aggiornamento
 * @param createdAt      l'istante di creazione della richiesta di aggiornamento
 *
 * @see com.gameplatform.shared.dto.UpdateTournamentUseCase
 * @see com.gameplatform.shared.dto.SyncEventProcessor
 */
public record TournamentUpdateRequestedEventDto(
        String eventId,
        String eventType,
        String requestId,
        String actingUserId,
        String actingRole,
        String buildingId,
        String tournamentId,
        String name,
        Instant startsAt,
        List<String> buildingIds,
        Instant createdAt
) {
}