package com.gameplatform.shared.dto;

import java.util.List;

/**
 * DTO (Data Transfer Object) che rappresenta la richiesta di iscrizione di una squadra a un torneo.
 * Contiene il nome della squadra e l'elenco dei suoi membri, ed è utilizzato per trasferire
 * tali dati tra i livelli dell'applicazione durante la registrazione di un partecipante.
 *
 * @see TournamentDto
 */
public record RegisterTournamentParticipantDto(
        /**
         * Restituisce il nome della squadra che richiede l'iscrizione al torneo.
         *
         * @return il nome della squadra; non è {@code null} e non è una stringa vuota
         */
        String teamName,

        /**
         * Restituisce l'elenco dei nomi dei membri che compongono la squadra.
         *
         * @return la lista dei nomi dei membri; non è {@code null} e, se la squadra non
         *         ha membri, è una lista vuota (mai {@code null})
         */
        List<String> teamMembers
) {
}
