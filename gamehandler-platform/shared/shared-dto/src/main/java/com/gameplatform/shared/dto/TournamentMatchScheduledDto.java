package com.gameplatform.shared.dto;

import com.gameplatform.shared.domain.model.GameType;
import java.time.Instant;

/**
 * DTO che rappresenta un incontro di torneo pianificato (schedulato) all'interno della piattaforma.
 * Trasporta i dati essenziali relativi all'evento, ai partecipanti, al gioco e alla sede,
 * nonché al momento previsto per lo svolgimento dell'incontro.
 *
 * @see GameType
 */
/**
 * Crea un nuovo DTO che descrive un incontro di torneo pianificato.
 *
 * @param eventId        identificativo univoco dell'evento associato all'incontro; non deve essere {@code null} né vuoto.
 * @param eventType      tipologia dell'evento (es. creazione, aggiornamento, cancellazione); non deve essere {@code null} né vuoto.
 * @param matchId        identificativo univoco dell'incontro; non deve essere {@code null} né vuoto.
 * @param tournamentId   identificativo del torneo di appartenenza; non deve essere {@code null} né vuoto.
 * @param round          numero del turno dell'incontro all'interno del torneo; deve essere {@code >= 0}, con {@code 0} che indica tipicamente il primo turno.
 * @param bracketPosition posizione dell'incontro nel tabellone del torneo; deve essere {@code >= 0}.
 * @param participantA   identificativo del primo partecipante all'incontro; non deve essere {@code null} né vuoto.
 * @param participantB   identificativo del secondo partecipante all'incontro; non deve essere {@code null} né vuoto.
 * @param gameType       tipologia di gioco prevista per l'incontro; non deve essere {@code null}.
 * @param gameId         identificativo del gioco specifico associato all'incontro; non deve essere {@code null} né vuoto.
 * @param status         stato corrente dell'incontro (es. pianificato, in corso, completato); non deve essere {@code null} né vuoto.
 * @param scheduledAt    istante temporale previsto per lo svolgimento dell'incontro; non deve essere {@code null}.
 * @param buildingId     identificativo della sede o edificio in cui si svolge l'incontro; non deve essere {@code null} né vuoto.
 */
public record TournamentMatchScheduledDto(
        /**
         * Restituisce l'identificativo univoco dell'evento associato all'incontro.
         *
         * @return l'identificativo dell'evento; non {@code null} né vuoto.
         */
        String eventId,
        /**
         * Restituisce la tipologia dell'evento rappresentato dal DTO.
         *
         * @return la tipologia dell'evento; non {@code null} né vuoto.
         */
        String eventType,
        /**
         * Restituisce l'identificativo univoco dell'incontro.
         *
         * @return l'identificativo dell'incontro; non {@code null} né vuoto.
         */
        String matchId,
        /**
         * Restituisce l'identificativo del torneo di appartenenza dell'incontro.
         *
         * @return l'identificativo del torneo; non {@code null} né vuoto.
         */
        String tournamentId,
        /**
         * Restituisce il numero del turno in cui si svolge l'incontro.
         *
         * @return il numero del turno; {@code >= 0}, con {@code 0} che indica tipicamente il primo turno.
         */
        int round,
        /**
         * Restituisce la posizione dell'incontro all'interno del tabellone del torneo.
         *
         * @return la posizione nel tabellone; {@code >= 0}.
         */
        int bracketPosition,
        /**
         * Restituisce l'identificativo del primo partecipante all'incontro.
         *
         * @return l'identificativo del primo partecipante; non {@code null} né vuoto.
         */
        String participantA,
        /**
         * Restituisce l'identificativo del secondo partecipante all'incontro.
         *
         * @return l'identificativo del secondo partecipante; non {@code null} né vuoto.
         */
        String participantB,
        /**
         * Restituisce la tipologia di gioco prevista per l'incontro.
         *
         * @return la tipologia di gioco; non {@code null}.
         */
        GameType gameType,
        /**
         * Restituisce l'identificativo del gioco specifico associato all'incontro.
         *
         * @return l'identificativo del gioco; non {@code null} né vuoto.
         */
        String gameId,
        /**
         * Restituisce lo stato corrente dell'incontro.
         *
         * @return lo stato dell'incontro; non {@code null} né vuoto.
         */
        String status,
        /**
         * Restituisce l'istante temporale previsto per lo svolgimento dell'incontro.
         *
         * @return l'istante di pianificazione; non {@code null}.
         */
        Instant scheduledAt,
        /**
         * Restituisce l'identificativo della sede o edificio in cui si svolge l'incontro.
         *
         * @return l'identificativo della sede; non {@code null} né vuoto.
         */
        String buildingId
) {
}
