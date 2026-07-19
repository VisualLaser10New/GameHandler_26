package com.gameplatform.shared.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Corpo della richiesta per aggiungere un gioco al catalogo di un edificio da parte
 * di un amministratore locale. Trasporta i dati necessari a identificare il tipo di
 * gioco e il nome leggibile del dispositivo o tavolo da registrare.
 *
 * @param gameType il valore letterale del tipo di gioco; corrisponde a un valore
 *                 dell'enum {@code GameType} (ad esempio {@code CHESS},
 *                 {@code FOOSBALL}) e non può essere nullo né vuoto
 * @param name     il nome leggibile del dispositivo o tavolo (ad esempio
 *                 "Chess Table 2"); non può essere nullo né vuoto
 */
public record CreateGameRequestDto(
        @NotBlank String gameType,
        @NotBlank String name
) {
}