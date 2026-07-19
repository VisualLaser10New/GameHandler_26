package com.gameplatform.shared.dto;

import com.gameplatform.shared.domain.model.TournamentMatchStatus;
import java.time.Instant;

/**
 * Rappresenta un incontro di un torneo all'interno della piattaforma di gioco.
 * Contiene i dati identificativi dell'incontro, i partecipanti, la struttura a
 * tabellone, lo stato corrente e la pianificazione temporale.
 *
 * @see TournamentMatchStatus
 */
public record TournamentMatchDto(
        /**
         * Identificativo univoco dell'incontro.
         * Non deve essere {@code null} né vuoto.
         */
        String id,
        /**
         * Numero del turno (round) a cui appartiene l'incontro all'interno del torneo.
         * Valore intero maggiore o uguale a {@code 0}; il round {@code 0} indica tipicamente
         * il primo turno del tabellone.
         */
        int round,
        /**
         * Posizione dell'incontro all'interno del tabellone del turno corrente.
         * Valore intero maggiore o uguale a {@code 0} che ordina gli incontri nel bracket.
         */
        int bracketPosition,
        /**
         * Identificativo del primo partecipante all'incontro.
         * Non deve essere {@code null} né vuoto.
         */
        String participantA,
        /**
         * Identificativo del secondo partecipante all'incontro.
         * Non deve essere {@code null} né vuoto.
         */
        String participantB,
        /**
         * Identificativo della postazione o struttura (building) che ospita l'incontro.
         * Non deve essere {@code null} né vuoto.
         */
        String buildingId,
        /**
         * Identificativo del gioco associato all'incontro.
         * Non deve essere {@code null} né vuoto.
         */
        String gameId,
        /**
         * Stato corrente dell'incontro, che ne descrive l'avanzamento nel torneo.
         * Non deve essere {@code null}.
         *
         * @see TournamentMatchStatus
         */
        TournamentMatchStatus status,
        /**
         * Istante di tempo in cui l'incontro è pianificato.
         * Può essere {@code null} se l'incontro non è ancora stato schedulato.
         */
        Instant scheduledAt,
        /**
         * Identificativo del partecipante vincente dell'incontro.
         * Può essere {@code null} se l'incontro non è ancora concluso oppure in caso di pareggio non risolto.
         */
        String winner
) {
}
