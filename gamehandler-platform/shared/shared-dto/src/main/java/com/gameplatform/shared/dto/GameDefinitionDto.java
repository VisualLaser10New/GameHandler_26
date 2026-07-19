package com.gameplatform.shared.dto;

import com.gameplatform.shared.domain.model.GameType;
import java.util.Map;

/**
 * DTO che rappresenta la definizione di un gioco all'interno della piattaforma.
 * Incapsula le informazioni essenziali di configurazione, quali tipologia, nome,
 * limiti di giocatori, possibilità di formare squadre e regole di registrazione.
 *
 * @see com.gameplatform.shared.domain.model.GameType
 */
public record GameDefinitionDto(
        /**
         * Tipologia del gioco cui appartiene la definizione.
         *
         * @param gameType tipologia di gioco; non deve essere {@code null}.
         * @return la tipologia di gioco associata alla definizione.
         */
        GameType gameType,
        /**
         * Nome identificativo del gioco.
         *
         * @param name nome del gioco; non deve essere {@code null} né una stringa vuota.
         * @return il nome del gioco.
         */
        String name,
        /**
         * Numero minimo di giocatori richiesti per avviare il gioco.
         *
         * @param minPlayers numero minimo di giocatori; deve essere maggiore o uguale a {@code 1}.
         * @return il numero minimo di giocatori.
         */
        int minPlayers,
        /**
         * Numero massimo di giocatori ammessi per il gioco.
         *
         * @param maxPlayers numero massimo di giocatori; deve essere maggiore o uguale a {@code minPlayers}.
         * @return il numero massimo di giocatori.
         */
        int maxPlayers,
        /**
         * Indica se la formazione di squadre è consentita per il gioco.
         *
         * @param teamAllowed {@code true} se le squadre sono ammesse, {@code false} altrimenti.
         * @return {@code true} se le squadre sono consentite, {@code false} in caso contrario.
         */
        boolean teamAllowed,
        /**
         * Regole di registrazione associate al gioco.
         *
         * @param registrationRules mappa delle regole di registrazione; non deve essere {@code null},
         *                          può essere vuota se non sono previste regole specifiche.
         * @return la mappa delle regole di registrazione, eventualmente vuota ma mai {@code null}.
         */
        Map<String, Object> registrationRules
) {
}