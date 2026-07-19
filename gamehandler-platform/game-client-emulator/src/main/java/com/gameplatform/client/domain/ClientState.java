package com.gameplatform.client.domain;

/**
 * Enumerazione che rappresenta gli stati possibili del client di gioco.
 * <p>
 * Definisce l'intero ciclo di vita della connessione: dall'assenza di connessione
 * ({@link #DISCONNECTED}) fino allo stato attivo di partita ({@link #IN_GAME}) e
 * alla sospensione temporanea ({@link #PAUSED}). Ogni stato implica un insieme
 * specifico di operazioni consentite e di risorse allocate lato client.
 * </p>
 */
public enum ClientState {
    /**
     * Stato iniziale e finale del client: nessuna connessione TCP attiva verso il server.
     * <p>
     * In questo stato il client non ha alcuna risorsa di rete allocata e nessun
     * identificativo utente associato. Qualsiasi operazione che richieda una
     * comunicazione con il server viene rifiutata.
     * </p>
     */
    DISCONNECTED,

    /**
     * Connessione TCP stabilita con il server, ma utente non ancora autenticato.
     * <p>
     * Il client ha aperto un socket e scambiato l'handshake di protocollo, ma non
     * ha ancora inviato credenziali valide. In questo stato sono consentite solo
     * le operazioni di login e di disconnessione.
     * </p>
     */
    CONNECTED,

    /**
     * Utente autenticato e pronto per la selezione della partita.
     * <p>
     * Il server ha verificato le credenziali e il client possiede un token di
     * sessione valido. L'utente può visualizzare la lista delle partite disponibili,
     * crearne di nuove o unirsi a una esistente.
     * </p>
     */
    LOGGED_IN,

    /**
     * Client attivamente impegnato in una partita in corso.
     * <p>
     * Il client ha superato la fase di matchmaking e partecipa a una sessione di
     * gioco attiva. In questo stato vengono scambiati messaggi di gioco in tempo
     * reale con il server. Le operazioni di modifica del profilo o di logout sono
     * temporaneamente sospese.
     * </p>
     */
    IN_GAME,

    /**
     * Partita temporaneamente sospesa dall'utente o dal server.
     * <p>
     * La sessione di gioco rimane attiva sul server ma il client ha sospeso
     * l'invio e la ricezione dei messaggi di gioco. Da questo stato &egrave; possibile
     * riprendere la partita ({@link #IN_GAME}) o abbandonarla tornando a
     * {@link #LOGGED_IN}.
     * </p>
     */
    PAUSED
}
