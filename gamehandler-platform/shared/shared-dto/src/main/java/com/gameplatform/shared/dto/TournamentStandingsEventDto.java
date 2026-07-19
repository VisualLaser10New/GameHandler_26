package com.gameplatform.shared.dto;

import java.time.Instant;
import java.util.List;

/**
 * Payload di outbox per l'evento {@code TOURNAMENT_STANDINGS_UPSERTED} nel flusso di
 * replica Centrale→Locale (PIANO §7.A.3). Trasporta uno snapshot completo della
 * classifica di un singolo torneo, consentendo al nodo locale di sostituire la propria
 * proiezione in modo idempotente (cancellazione e reinserimento per {@code tournamentId}).
 *
 * <p>Il componente {@code originatingRequestId} è opzionale: vale {@code null} per gli
 * eventi generati sul percorso FASE 5/6 (classifica ricalcolata al termine di una
 * partita) e valorizzato sul percorso SyncEventProcessor §7.A.3, dove riporta
 * l'identificativo dell'evento di outbox originario per il tracciamento
 * dell'idempotenza e la chiusura della richiesta amministrativa.</p>
 *
 * @see TournamentStandingDto
 */
/**
 * Costruisce lo snapshot completo della classifica di torneo veicolato dall'evento.
 *
 * @param eventId              identificativo dell'evento di outbox (UUID)
 * @param eventType            tipo dell'evento, sempre {@code TOURNAMENT_STANDINGS_UPSERTED}
 * @param tournamentId         identificativo del torneo a cui si riferisce la classifica
 * @param entries              snapshot completo delle voci di classifica
 * @param originatingRequestId identificativo della richiesta/evento originante (opzionale, può essere {@code null})
 * @param updatedAt            istante dell'ultima modifica della classifica
 */
public record TournamentStandingsEventDto(
        String eventId,
        String eventType,
        String tournamentId,
        List<TournamentStandingDto> entries,
        String originatingRequestId,
        Instant updatedAt
) {
    /**
     * Costruttore di comodo per gli eventi privi di richiesta originante, in cui
     * {@code originatingRequestId} viene impostato a {@code null} (percorso FASE 5/6).
     *
     * @param eventId      identificativo dell'evento di outbox (UUID)
     * @param eventType    tipo dell'evento, sempre {@code TOURNAMENT_STANDINGS_UPSERTED}
     * @param tournamentId identificativo del torneo a cui si riferisce la classifica
     * @param entries      snapshot completo delle voci di classifica
     * @param updatedAt    istante dell'ultima modifica della classifica
     */
    public TournamentStandingsEventDto(String eventId, String eventType, String tournamentId,
                                      List<TournamentStandingDto> entries, Instant updatedAt) {
        this(eventId, eventType, tournamentId, entries, null, updatedAt);
    }
}