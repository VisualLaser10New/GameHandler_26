package com.gameplatform.shared.mqtt.payload;

/**
 * Payload MQTT trasmesso sul topic {@code building/{id}/game/{gameId}/session/turn}
 * ogni volta che un giocatore conclude il proprio turno in una partita multigiocatore
 * a turni (Scacchi, Risiko, Freccette, Monopoli). I client sottoscritti al topic
 * applicano l'aggiornamento remoto del turno affinché ogni emulatore mostri lo stesso
 * giocatore attivo e solo il giocatore di turno possa effettuare azioni.
 *
 * <p>I tre componenti del record identificano in modo univoco la sessione di gioco,
 * l'indice progressivo del turno e il nome del giocatore cui spetta il turno successivo.</p>
 *
 * @see com.gameplatform.shared.mqtt.payload.SessionPayload
 * @see com.gameplatform.shared.mqtt.payload.GamePayload
 */
public record TurnPayload(
        /**
         * Identificatore univoco della sessione di gioco a cui il turno appartiene.
         * Non deve essere {@code null} né una stringa vuota; in caso contrario il
         * messaggio non può essere associato a una sessione valida.
         */
        String sessionId,

        /**
         * Indice progressivo del turno all'interno della sessione. Assume valori
         * maggiori o uguali a {@code 0}; il valore {@code 0} indica il primo turno
         * della partita.
         */
        int turnIndex,

        /**
         * Nome del giocatore cui spetta il turno successivo. Non deve essere
         * {@code null} né una stringa vuota; identifica il solo giocatore autorizzato
         * a compiere azioni fino al termine del turno corrente.
         */
        String playerName
) {

    /**
     * Restituisce l'identificatore univoco della sessione di gioco a cui il turno
     * appartiene.
     *
     * @return l'identificatore della sessione, non {@code null} né vuoto
     */
    public String sessionId() {
        return sessionId;
    }

    /**
     * Restituisce l'indice progressivo del turno all'interno della sessione.
     *
     * @return l'indice del turno, maggiore o uguale a {@code 0}; il valore
     *         {@code 0} corrisponde al primo turno della partita
     */
    public int turnIndex() {
        return turnIndex;
    }

    /**
     * Restituisce il nome del giocatore cui spetta il turno successivo.
     *
     * @return il nome del giocatore di turno, non {@code null} né vuoto
     */
    public String playerName() {
        return playerName;
    }
}
