package com.gameplatform.shared.mqtt.payload;

import com.gameplatform.shared.domain.model.WinCondition;

/**
 * Payload MQTT scambiato al termine di una sessione di gioco.
 * Incapsula l'identificativo della sessione, l'eventuale vincitore,
 * la condizione di vittoria applicata e i dati aggiuntivi del risultato.
 *
 * @see com.gameplatform.shared.domain.model.WinCondition
 */
public record SessionEndPayload(
    /**
     * Restituisce l'identificativo univoco della sessione di gioco terminata.
     *
     * @return l'ID della sessione, non {@code null} e non vuoto
     */
    String sessionId,

    /**
     * Restituisce l'identificativo del partecipante risultato vincitore.
     *
     * @return l'ID del vincitore, oppure {@code null} in caso di sessione
     *         conclusa senza un vincitore (es. pareggio o abbandono)
     */
    String winnerId,

    /**
     * Restituisce la condizione di vittoria che ha determinato la fine della sessione.
     *
     * @return la condizione di vittoria applicata, non {@code null}
     */
    WinCondition winCondition,

    /**
     * Restituisce i dati aggiuntivi relativi al risultato della sessione.
     *
     * @return i dati del risultato, oppure {@code null} o stringa vuota se
     *         non sono presenti informazioni supplementari
     */
    String resultData
) {}
