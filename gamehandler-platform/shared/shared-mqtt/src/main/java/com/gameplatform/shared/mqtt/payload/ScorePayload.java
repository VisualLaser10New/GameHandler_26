package com.gameplatform.shared.mqtt.payload;

import java.util.Collections;
import java.util.Map;

/**
 * Payload MQTT trasmesso sul topic {@code building/{id}/game/{gameId}/session/score}
 * ogni volta che il punteggio di un giocatore cambia all'interno di una partita
 * multigiocatore (ad esempio Darts o Foosball). I client sottoscritti al topic
 * applicano l'aggiornamento remoto del punteggio così da mantenere allineata la
 * classifica su ogni emulatore.
 *
 * <p>Trasporta uno snapshot completo delle entry {@code giocatore -> punteggio}
 * anziché un delta, consentendo a un client che entra a metà sequenza o che
 * perde un messaggio precedente di riconvergere comunque sui totali corretti.</p>
 *
 * @see com.gameplatform.shared.mqtt.payload.SessionPayload
 */
public record ScorePayload(
        String sessionId,
        Map<String, Integer> scores
) {

    /**
     * Restituisce l'identificatore univoco della sessione di gioco a cui
     * appartiene lo snapshot del punteggio.
     *
     * @return l'identificativo della sessione; non è {@code null} e non è
     *         vuoto quando il payload proviene da una partita valida
     */
    public String sessionId() {
        return sessionId;
    }

    /**
     * Restituisce lo snapshot completo dei punteggi associati a ciascun
     * giocatore della sessione.
     *
     * @return una mappa immutabile che associa l'identificativo del giocatore
     *         (chiave non {@code null}) al relativo punteggio intero; la mappa
     *         non è {@code null}, può essere vuota quando nessun giocatore ha
     *         ancora segnato e i punteggi possono assumere valore {@code 0} o
     *         negativo in caso di penalità
     * @see Collections#unmodifiableMap(Map)
     */
    public Map<String, Integer> scores() {
        return scores;
    }
}
