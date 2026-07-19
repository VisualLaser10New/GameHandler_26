package com.gameplatform.shared.dto;

import java.time.Instant;
import java.util.List;

/**
 * Payload dell'outbox per l'evento {@code TOURNAMENT_PARTICIPANTS_UPSERTED}
 * nel flusso di replica Central&rarr;Local (PIANO &sect;7.A.3). Trasporta uno
 * snapshot completo dei partecipanti per torneo affinché il nodo locale possa
 * sostituire la propria proiezione in modo idempotente (delete+insert per
 * {@code tournamentId}).
 *
 * <p>Il campo {@code originatingRequestId} è nullable: vale {@code null} per gli
 * eventi generati sul path FASE 4/5/6 (registrazione/annullamento) e non è
 * {@code null} per il path SyncEventProcessor &sect;7.A.3 (chiusura richiesta
 * admin).</p>
 *
 * @param eventId              identificativo dell'evento outbox (UUID)
 * @param eventType            sempre {@code TOURNAMENT_PARTICIPANTS_UPSERTED}
 * @param tournamentId         l'identificativo del torneo
 * @param participants         lo snapshot completo dei partecipanti
 * @param originatingRequestId id della richiesta/evento origine (nullable)
 * @param updatedAt            istante dell'ultima mutazione
 *
 * @see TournamentParticipantViewDto
 */
public record TournamentParticipantsEventDto(
        String eventId,
        String eventType,
        String tournamentId,
        List<TournamentParticipantViewDto> participants,
        String originatingRequestId,
        Instant updatedAt
) {
    /**
     * Costruttore di comodo per gli eventi generati sul path FASE 4/5/6
     * (registrazione/annullamento). Crea un payload con
     * {@code originatingRequestId} impostato a {@code null}.
     *
     * @param eventId      identificativo dell'evento outbox (UUID)
     * @param eventType    sempre {@code TOURNAMENT_PARTICIPANTS_UPSERTED}
     * @param tournamentId l'identificativo del torneo
     * @param participants lo snapshot completo dei partecipanti
     * @param updatedAt    istante dell'ultima mutazione
     *
     * @see #TournamentParticipantsEventDto(String, String, String, List, String, Instant)
     */
    public TournamentParticipantsEventDto(String eventId, String eventType, String tournamentId,
                                          List<TournamentParticipantViewDto> participants, Instant updatedAt) {
        this(eventId, eventType, tournamentId, participants, null, updatedAt);
    }
}