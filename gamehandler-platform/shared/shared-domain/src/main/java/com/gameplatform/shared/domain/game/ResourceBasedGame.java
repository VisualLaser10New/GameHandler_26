package com.gameplatform.shared.domain.game;

import com.gameplatform.shared.domain.model.UserId;

import java.util.Map;

/**
 * Interfaccia che definisce il contratto per i giochi basati sulla gestione di risorse.
 * Espone le operazioni per consultare lo stato delle risorse possedute dai giocatori
 * e per aggiornarne il valore nel corso di una partita.
 *
 * @see com.gameplatform.shared.domain.model.UserId
 */
public interface ResourceBasedGame {

    /**
     * Restituisce la mappa completa delle risorse possedute da ciascun giocatore.
     *
     * <p>L'insieme delle chiavi corrisponde ai giocatori partecipanti; ogni giocatore
     * è associato a una mappa che lega il nome della risorsa al relativo valore intero.
     * La mappa restituita non è {@code null} e può essere vuota se nessun giocatore
     * possiede risorse. I valori associati a ciascuna risorsa possono essere zero o
     * negativi a seconda delle regole del gioco.</p>
     *
     * @return mappa non {@code null} che associa a ogni {@link UserId} la propria
     *         mappa di risorse (nome risorsa → valore), eventualmente vuota
     *
     * @see #updateResource(UserId, String, int)
     */
    Map<UserId, Map<String, Integer>> getResources();

    /**
     * Aggiorna il valore di una risorsa posseduta da un giocatore.
     *
     * <p>Sostituisce il valore corrente della risorsa identificata da {@code resourceKey}
     * per il giocatore {@code player} con {@code newValue}. Se la risorsa non esiste
     * ancora per il giocatore, viene creata con il valore indicato.</p>
     *
     * @param player      identificativo del giocatore cui appartiene la risorsa;
     *                    non deve essere {@code null}
     * @param resourceKey nome della risorsa da aggiornare; non deve essere
     *                    {@code null} né vuoto
     * @param newValue    nuovo valore da assegnare alla risorsa; può essere zero,
     *                    positivo o negativo secondo le regole del gioco
     *
     * @throws IllegalArgumentException se {@code player} o {@code resourceKey} sono
     *                                  {@code null}, oppure se {@code resourceKey} è vuoto
     * @throws IllegalStateException    se il giocatore {@code player} non è parte
     *                                  della partita corrente
     *
     * @see #getResources()
     */
    void updateResource(UserId player, String resourceKey, int newValue);
}
