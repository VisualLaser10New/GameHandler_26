package com.gameplatform.shared.mqtt.payload;

/**
 * Payload MQTT trasmesso sul topic {@code building/{id}/game/{gameId}/session/move}
 * ogni volta che un giocatore effettua una mossa in un gioco multiplayer a
 * scacchiera (attualmente Chess). I client sottoscritti al topic applicano la
 * mossa remota affinché ogni emulatore mostri lo stesso stato della scacchiera.
 *
 * <p>La mossa è espressa come coordinate di scacchiera sorgente e destinazione
 * (coppie riga/colonna a base zero). Il campo opzionale {@code capturedPiece}
 * contiene il glifo Unicode del pezzo presente nella cella di destinazione, se
 * esistente, così il client ricevente può registrarlo nell'elenco dei pezzi
 * catturati.</p>
 *
 * @see com.gameplatform.shared.mqtt.payload.SessionPayload
 */
public record MovePayload(
        String sessionId,
        int fromRow,
        int fromCol,
        int toRow,
        int toCol,
        String capturedPiece
) {

    /**
     * Restituisce l'identificatore della sessione di gioco a cui appartiene la mossa.
     *
     * @return l'identificatore della sessione; non è {@code null} e non è vuoto
     */
    public String sessionId() {
        return sessionId;
    }

    /**
     * Restituisce l'indice di riga a base zero della cella di origine della mossa.
     *
     * @return l'indice di riga sorgente; è un valore compreso tra {@code 0} e
     *         l'ultima riga della scacchiera
     */
    public int fromRow() {
        return fromRow;
    }

    /**
     * Restituisce l'indice di colonna a base zero della cella di origine della mossa.
     *
     * @return l'indice di colonna sorgente; è un valore compreso tra {@code 0} e
     *         l'ultima colonna della scacchiera
     */
    public int fromCol() {
        return fromCol;
    }

    /**
     * Restituisce l'indice di riga a base zero della cella di destinazione della mossa.
     *
     * @return l'indice di riga destinazione; è un valore compreso tra {@code 0} e
     *         l'ultima riga della scacchiera
     */
    public int toRow() {
        return toRow;
    }

    /**
     * Restituisce l'indice di colonna a base zero della cella di destinazione della mossa.
     *
     * @return l'indice di colonna destinazione; è un valore compreso tra {@code 0} e
     *         l'ultima colonna della scacchiera
     */
    public int toCol() {
        return toCol;
    }

    /**
     * Restituisce il glifo Unicode del pezzo presente nella cella di destinazione
     * prima della mossa, se la mossa ha catturato un pezzo.
     *
     * @return il glifo Unicode del pezzo catturato, oppure {@code null} se la mossa
     *         non ha catturato alcun pezzo
     */
    public String capturedPiece() {
        return capturedPiece;
    }
}
