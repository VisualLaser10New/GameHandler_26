package com.gameplatform.shared.mqtt;

/**
 * Fornisce le costanti di utilità per la costruzione dei topic MQTT
 * utilizzati dalla piattaforma di gioco per scambiare messaggi tra
 * emulatori e client. Ogni metodo restituisce il percorso completo di
 * un topic a partire dagli identificativi di edificio e di partita.
 *
 * @see com.gameplatform.shared.mqtt.MqttClient
 */
public final class MqttTopics {
    private MqttTopics() {}

    /**
     * Restituisce il topic sul quale viene pubblicato lo stato corrente
     * di una partita all'interno di un edificio.
     *
     * @param buildingId identificativo dell'edificio; non deve essere
     *                   {@code null} o vuoto
     * @param gameId     identificativo della partita; non deve essere
     *                   {@code null} o vuoto
     * @return il topic MQTT completo dello stato di gioco, mai {@code null}
     * @throws NullPointerException se {@code buildingId} o {@code gameId}
     *                              sono {@code null}
     */
    public static String gameState(String buildingId, String gameId) {
        return "building/" + buildingId + "/game/" + gameId + "/state";
    }

    /**
     * Restituisce il topic sul quale un client segnala l'avvio di una
     * nuova sessione di gioco per una partita all'interno di un edificio.
     *
     * @param buildingId identificativo dell'edificio; non deve essere
     *                   {@code null} o vuoto
     * @param gameId     identificativo della partita; non deve essere
     *                   {@code null} o vuoto
     * @return il topic MQTT di avvio sessione, mai {@code null}
     * @throws NullPointerException se {@code buildingId} o {@code gameId}
     *                              sono {@code null}
     * @see #sessionEnd(String, String)
     */
    public static String sessionStart(String buildingId, String gameId) {
        return "building/" + buildingId + "/game/" + gameId + "/session/start";
    }

    /**
     * Restituisce il topic sul quale un client segnala la terminazione
     * di una sessione di gioco per una partita all'interno di un edificio.
     *
     * @param buildingId identificativo dell'edificio; non deve essere
     *                   {@code null} o vuoto
     * @param gameId     identificativo della partita; non deve essere
     *                   {@code null} o vuoto
     * @return il topic MQTT di fine sessione, mai {@code null}
     * @throws NullPointerException se {@code buildingId} o {@code gameId}
     *                              sono {@code null}
     * @see #sessionStart(String, String)
     */
    public static String sessionEnd(String buildingId, String gameId) {
        return "building/" + buildingId + "/game/" + gameId + "/session/end";
    }

    /**
     * Restituisce il topic sul quale un client segnala la sospensione
     * temporanea di una sessione di gioco per una partita all'interno
     * di un edificio.
     *
     * @param buildingId identificativo dell'edificio; non deve essere
     *                   {@code null} o vuoto
     * @param gameId     identificativo della partita; non deve essere
     *                   {@code null} o vuoto
     * @return il topic MQTT di pausa sessione, mai {@code null}
     * @throws NullPointerException se {@code buildingId} o {@code gameId}
     *                              sono {@code null}
     * @see #sessionResume(String, String)
     */
    public static String sessionPause(String buildingId, String gameId) {
        return "building/" + buildingId + "/game/" + gameId + "/session/pause";
    }

    /**
     * Restituisce il topic sul quale un client segnala la ripresa di una
     * sessione di gioco precedentemente sospesa per una partita all'interno
     * di un edificio.
     *
     * @param buildingId identificativo dell'edificio; non deve essere
     *                   {@code null} o vuoto
     * @param gameId     identificativo della partita; non deve essere
     *                   {@code null} o vuoto
     * @return il topic MQTT di ripresa sessione, mai {@code null}
     * @throws NullPointerException se {@code buildingId} o {@code gameId}
     *                              sono {@code null}
     * @see #sessionPause(String, String)
     */
    public static String sessionResume(String buildingId, String gameId) {
        return "building/" + buildingId + "/game/" + gameId + "/session/resume";
    }

    /**
     * Restituisce il topic sul quale i client di giochi multiplayer a turni
     * pubblicano i cambi di turno (es. scacchi, risiko, freccette, monopoli).
     * Quando un giocatore termina il proprio turno, ogni emulatore sottoscritto
     * riceve il nuovo indice di turno e il nome del giocatore attivo mantenendo
     * tutti i client sincronizzati.
     *
     * @param buildingId identificativo dell'edificio; non deve essere
     *                   {@code null} o vuoto
     * @param gameId     identificativo della partita; non deve essere
     *                   {@code null} o vuoto
     * @return il topic MQTT di cambio turno, mai {@code null}
     * @throws NullPointerException se {@code buildingId} o {@code gameId}
     *                              sono {@code null}
     * @see #sessionMove(String, String)
     */
    public static String sessionTurn(String buildingId, String gameId) {
        return "building/" + buildingId + "/game/" + gameId + "/session/turn";
    }

    /**
     * Restituisce il topic sul quale i client di giochi da tavolo multiplayer
     * pubblicano le singole mosse dei pezzi (attualmente scacchi). Quando un
     * giocatore muove un pezzo, ogni emulatore sottoscritto riceve la mossa e
     * la applica in modo che tutti i client mostrino la stessa scacchiera.
     *
     * @param buildingId identificativo dell'edificio; non deve essere
     *                   {@code null} o vuoto
     * @param gameId     identificativo della partita; non deve essere
     *                   {@code null} o vuoto
     * @return il topic MQTT di mossa, mai {@code null}
     * @throws NullPointerException se {@code buildingId} o {@code gameId}
     *                              sono {@code null}
     * @see #sessionTurn(String, String)
     */
    public static String sessionMove(String buildingId, String gameId) {
        return "building/" + buildingId + "/game/" + gameId + "/session/move";
    }

    /**
     * Restituisce il topic sul quale i client di giochi multiplayer pubblicano
     * gli aggiornamenti del punteggio (es. freccette, calcio balilla). Quando
     * il punteggio di un giocatore cambia, ogni emulatore sottoscritto riceve
     * l'istantanea completa dei punteggi in modo che tutti i client mostrino
     * la stessa classifica.
     *
     * @param buildingId identificativo dell'edificio; non deve essere
     *                   {@code null} o vuoto
     * @param gameId     identificativo della partita; non deve essere
     *                   {@code null} o vuoto
     * @return il topic MQTT di punteggio, mai {@code null}
     * @throws NullPointerException se {@code buildingId} o {@code gameId}
     *                              sono {@code null}
     */
    public static String sessionScore(String buildingId, String gameId) {
        return "building/" + buildingId + "/game/" + gameId + "/session/score";
    }

    /**
     * Restituisce il topic sul quale un client o un emulatore pubblica i
     * battiti periodici per segnalare la propria presenza attiva per una
     * partita all'interno di un edificio.
     *
     * @param buildingId identificativo dell'edificio; non deve essere
     *                   {@code null} o vuoto
     * @param gameId     identificativo della partita; non deve essere
     *                   {@code null} o vuoto
     * @return il topic MQTT di heartbeat, mai {@code null}
     * @throws NullPointerException se {@code buildingId} o {@code gameId}
     *                              sono {@code null}
     * @see #heartbeatAck(String, String)
     */
    public static String heartbeat(String buildingId, String gameId) {
        return "building/" + buildingId + "/game/" + gameId + "/heartbeat";
    }

    /**
     * Restituisce il topic sul quale viene pubblicato l'acknowledgement di
     * un battito ricevuto, a conferma della presenza attiva di un client o
     * di un emulatore per una partita all'interno di un edificio.
     *
     * @param buildingId identificativo dell'edificio; non deve essere
     *                   {@code null} o vuoto
     * @param gameId     identificativo della partita; non deve essere
     *                   {@code null} o vuoto
     * @return il topic MQTT di acknowledgement heartbeat, mai {@code null}
     * @throws NullPointerException se {@code buildingId} o {@code gameId}
     *                              sono {@code null}
     * @see #heartbeat(String, String)
     */
    public static String heartbeatAck(String buildingId, String gameId) {
        return "building/" + buildingId + "/game/" + gameId + "/heartbeat/ack";
    }

    /**
     * Restituisce il topic sul quale vengono pubblicati gli avvisi e le
     * segnalazioni a livello di edificio, indipendentemente dalla partita.
     *
     * @param buildingId identificativo dell'edificio; non deve essere
     *                   {@code null} o vuoto
     * @return il topic MQTT degli avvisi, mai {@code null}
     * @throws NullPointerException se {@code buildingId} è {@code null}
     */
    public static String alerts(String buildingId) {
        return "building/" + buildingId + "/alerts";
    }
}
