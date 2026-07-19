package com.gameplatform.shared.dto;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentStatus;

import java.time.Instant;
import java.util.List;

/**
 * Proiezione di tipo read-model che rappresenta un riepilogo di torneo
 * destinato all'endpoint di elenco tornei per il giocatore Local,
 * esposto come {@code GET /api/tournaments[?status=]} (PIANO §7.B).
 *
 * <p>I dati sono originati dalla replica {@code tournaments_summary_local}
 * e rispecchiano la proiezione di dominio persistita
 * {@code TournamentSummaryLocal}, ma non includono il flag tombstone
 * {@code deleted}: vengono restituite esclusivamente le righe non eliminate.</p>
 *
 * <p>Questo record funge da DTO di trasporto e aggrega le informazioni
 * essenziali di un torneo (identificativo, nome, tipo di gioco, modalità a
 * squadre, stato, finestra temporale e conteggio dei partecipanti) per
 * consentirne la visualizzazione in elenchi sintetici lato client.</p>
 *
 * @param tournamentId       identificativo univoco del torneo.
 * @param name               nome descrittivo del torneo mostrato all'utente.
 * @param gameType           tipo di gioco associato al torneo.
 * @param teamBased          {@code true} se il torneo si svolge a squadre,
 *                           {@code false} se individuale.
 * @param teamSize           dimensione di ciascuna squadra; vale 1 nel caso
 *                           di torneo individuale.
 * @param status             stato corrente del torneo.
 * @param startsAt           istante pianificato di inizio del torneo.
 * @param endsAt             istante pianificato di fine del torneo; può
 *                           essere {@code null} se non ancora definito.
 * @param buildingIds        identificativi degli edifici che ospitano il
 *                           torneo.
 * @param participantsCount  numero di partecipanti attualmente registrati.
 * @param updatedAt          istante dell'ultima mutazione dei dati del
 *                           torneo.
 *
 * @see com.gameplatform.shared.domain.model.TournamentSummaryLocal
 * @see com.gameplatform.shared.domain.model.GameType
 * @see com.gameplatform.shared.domain.model.TournamentStatus
 */
public record TournamentSummaryDto(
        String tournamentId,
        String name,
        GameType gameType,
        boolean teamBased,
        int teamSize,
        TournamentStatus status,
        Instant startsAt,
        Instant endsAt,
        List<String> buildingIds,
        int participantsCount,
        Instant updatedAt
) {
}
