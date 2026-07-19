package com.gameplatform.shared.dto;

import java.time.Instant;
import java.util.List;

/**
 * Payload dell'outbox per l'evento {@code TEAM_MEMBERS_UPSERTED} nel flusso di
 * replica Central&rarr;Local (BUG-TEAM-3). Trasporta uno snapshot completo
 * dell'appartenenza utente&rarr;squadra per torneo, affinché il nodo locale
 * possa sostituire la propria proiezione {@code team_members_local} in modo
 * idempotente (delete+insert per {@code tournamentId}).
 *
 * <p>Sosia strutturale di {@link TournamentParticipantsEventDto}. Il campo
 * {@code originatingRequestId} è {@code null} sul percorso del produttore
 * ({@code TournamentRegistrationService.registerTeam} /
 * {@code unregister}); la chiusura della admin-request per il caso d'uso di
 * registrazione è gestita dall'evento di ritorno parallelo
 * {@code TOURNAMENT_PARTICIPANTS_UPSERTED}
 * (vedi {@code TournamentParticipantsLocalSyncService.markCompletedIfRequested}),
 * perciò questo evento non provoca mai una transizione di stato su
 * {@code admin_requests_local} lato Local.
 *
 * @param eventId              id dell'evento outbox (UUID)
 * @param eventType            sempre {@code TEAM_MEMBERS_UPSERTED}
 * @param tournamentId         id del torneo di riferimento
 * @param teams               snapshot completo dell'appartenenza squadra&rarr;utente per torneo
 * @param originatingRequestId id della richiesta/evento originario (può essere {@code null})
 * @param updatedAt            istante dell'ultima mutazione
 *
 * @see TournamentParticipantsEventDto
 */
public record TeamMembersEventDto(
        String eventId,
        String eventType,
        String tournamentId,
        List<TeamMemberEntryDto> teams,
        String originatingRequestId,
        Instant updatedAt
) {
    /**
     * Costruisce il DTO impostando {@code originatingRequestId} a {@code null}.
     *
     * <p>Da utilizzare sul percorso del produttore, quando l'evento non deriva
     * da una richiesta originaria da chiudere lato Local.
     *
     * @param eventId      id dell'evento outbox (UUID), non deve essere {@code null}
     * @param eventType    tipo di evento, sempre {@code TEAM_MEMBERS_UPSERTED}
     * @param tournamentId id del torneo di riferimento, non deve essere {@code null}
     * @param teams        snapshot completo dell'appartenenza squadra&rarr;utente per torneo;
     *                     non deve essere {@code null}; una lista vuota rappresenta
     *                     l'assenza di appartenenze per il torneo
     * @param updatedAt    istante dell'ultima mutazione, non deve essere {@code null}
     */
    public TeamMembersEventDto(String eventId, String eventType, String tournamentId,
                               List<TeamMemberEntryDto> teams, Instant updatedAt) {
        this(eventId, eventType, tournamentId, teams, null, updatedAt);
    }
}