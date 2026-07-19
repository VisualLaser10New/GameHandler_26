package com.gameplatform.shared.domain.game;

/**
 * Rappresenta un gioco da tavolo gestibile a livello di dominio, definendo le
 * operazioni necessarie per persistere e ricostruire lo stato della partita.
 * Le implementazioni espongono i metodi per serializzare e ripristinare lo
 * stato della scacchiera in modo da supportare salvataggio e ripresa del gioco.
 *
 * @see com.gameplatform.shared.domain.game.Game
 */
public interface BoardGame {

    /**
     * Serializza lo stato corrente della scacchiera in una stringa.
     *
     * <p>Restituisce una rappresentazione testuale completa e non nulla dello
     * stato della partita, utilizzabile per il salvataggio o la trasmissione.
     * La stringa non è vuota quando la scacchiera è in uno stato valido.</p>
     *
     * @return la stringa che rappresenta lo stato serializzato della scacchiera,
     *         mai {@code null} e non vuota se la partita è in uno stato valido
     * @see #restoreBoardState(String)
     */
    String serializeBoardState();

    /**
     * Ripristina lo stato della scacchiera a partire da una rappresentazione
     * serializzata precedentemente ottenuta con {@link #serializeBoardState()}.
     *
     * <p>Imposta lo stato interno della partita in modo coerente con i dati
     * forniti. Se lo stato è vuoto o non valido, il comportamento dipende
     * dall'implementazione.</p>
     *
     * @param serializedState la stringa contenente lo stato da ripristinare,
     *                        non deve essere {@code null} né vuota
     * @throws IllegalArgumentException se {@code serializedState} è {@code null},
     *                                  è vuoto o non rappresenta uno stato valido
     * @see #serializeBoardState()
     */
    void restoreBoardState(String serializedState);
}
