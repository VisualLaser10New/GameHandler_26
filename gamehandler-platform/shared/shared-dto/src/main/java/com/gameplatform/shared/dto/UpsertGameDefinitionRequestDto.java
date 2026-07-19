package com.gameplatform.shared.dto;

import com.gameplatform.shared.domain.model.GameType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * DTO (Data Transfer Object) che rappresenta la richiesta di creazione o aggiornamento
 * di una definizione di gioco. Incapsula i metadati essenziali necessari al sistema per
 * registrare o modificare una definizione, garantendo la validazione dei vincoli minimi.
 *
 * <p>I campi sono sottoposti a vincoli di validazione lato server tramite le annotazioni
 * di Jakarta Validation (es. {@code @NotNull}, {@code @NotBlank}, {@code @Min}, {@code @Max}).</p>
 *
 * @see com.gameplatform.shared.domain.model.GameType
 */
public record UpsertGameDefinitionRequestDto(
        /**
         * Il tipo di gioco associato alla definizione. Non deve essere {@code null}.
         *
         * @param gameType il {@link com.gameplatform.shared.domain.model.GameType} del gioco
         * @return il tipo di gioco associato alla definizione
         */
        @NotNull GameType gameType,
        /**
         * Il nome visualizzato della definizione di gioco. Non deve essere vuoto o composto
         * solo da spazi bianchi.
         *
         * @param name il nome del gioco
         * @return il nome della definizione di gioco
         */
        @NotBlank String name,
        /**
         * Il numero minimo di giocatori richiesti per il gioco. Deve essere compreso tra 1 e 100.
         *
         * @param minPlayers il numero minimo di giocatori
         * @return il numero minimo di giocatori
         */
        @Min(1) @Max(100) int minPlayers,
        /**
         * Il numero massimo di giocatori ammessi per il gioco. Deve essere compreso tra 1 e 100.
         *
         * @param maxPlayers il numero massimo di giocatori
         * @return il numero massimo di giocatori
         */
        @Min(1) @Max(100) int maxPlayers,
        /**
         * Indica se il gioco consente la formazione di squadre. Se {@code true}, il gioco
         * supporta partite a squadre; in caso contrario, solo giocatori singoli.
         *
         * @param teamAllowed {@code true} se le squadre sono ammesse, {@code false} altrimenti
         * @return {@code true} se le squadre sono ammesse, {@code false} altrimenti
         */
        boolean teamAllowed,
        /**
         * Regole di registrazione specifiche del gioco, espresse come mappa chiave-valore libera.
         * Può essere {@code null} o vuota qualora non siano previste regole aggiuntive.
         *
         * @param registrationRules mappa di regole di registrazione opzionali
         * @return le regole di registrazione, o {@code null} se non definite
         */
        Map<String, Object> registrationRules
) {
}