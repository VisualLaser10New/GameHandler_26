package com.gameplatform.shared.dto;

import java.time.Instant;

/**
 * Vista di read-model di un singolo partecipante a un torneo (individuale o squadra)
 * per il flusso di replicazione Central&rarr;Local {@code TOURNAMENT_PARTICIPANTS_UPSERTED}
 * (PIANO &sect;7.A.3 / &sect;7.B).
 *
 * <p>A differenza di {@link TournamentParticipantDto}, che trasporta esclusivamente
 * lo snapshot di identit&agrave; e nome utilizzato dall'endpoint REST del Central, questa
 * vista include anche l'istante di registrazione affinch&eacute; la proiezione locale
 * possa preservare l'ordine di iscrizione impiegato dal costruttore del tabellone.</p>
 *
 * <p>Questo record &egrave; immutabile e funge da Data Transfer Object: espone i componenti
 * {@code participantId}, {@code isTeam}, {@code displayName} e {@code registeredAt} tramite
 * gli accessor generati automaticamente, utilizzati per ricostruire la proiezione locale
 * dei partecipanti.</p>
 *
 * @param participantId identificativo del partecipante (un valore {@code UserId} quando
 *                      {@code isTeam == false}, un valore {@code TeamId} altrimenti)
 * @param isTeam        {@code true} se il partecipante &egrave; una squadra, {@code false} se &egrave; un individuo
 * @param displayName   nome da visualizzare (username per gli individui, nome della squadra per le squadre)
 * @param registeredAt  istante di registrazione del partecipante al torneo
 *
 * @see TournamentParticipantDto
 */
public record TournamentParticipantViewDto(
        String participantId,
        boolean isTeam,
        String displayName,
        Instant registeredAt
) {
}