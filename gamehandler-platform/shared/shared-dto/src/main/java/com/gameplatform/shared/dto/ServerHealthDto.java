package com.gameplatform.shared.dto;

import java.time.Instant;

/**
 * Snapshot dello stato di salute di un singolo server locale, restituito
 * dall'endpoint amministrativo centralizzato che elenca i server registrati.
 *
 * <p>Il record rappresenta una vista immutabile e aggregata delle informazioni
 * essenziali di un server, utile per il monitoraggio e il coordinamento tra i
 * nodi della piattaforma.</p>
 *
 * @param buildingId identificativo univoco dell'edificio associato al server;
 *                  non deve essere {@code null} e non deve essere vuoto.
 * @param baseUrl    URL di base del server locale; non deve essere {@code null}
 *                  e costituisce l'endpoint per le comunicazioni inter-server.
 * @param lastSeenAt istante dell'ultimo segnale di attività ricevuto dal server;
 *                  non deve essere {@code null} e rappresenta un'istante nel passato
 *                  o nel presente.
 * @param active     indica se il server è attualmente considerato attivo;
 *                  vale {@code false} quando il server risulta non raggiungibile
 *                  o silente oltre la soglia di obsolescenza.
 * @param pendingReplicationCount numero di eventi di replicazione utente ancora
 *                  in attesa per il server; è un valore non negativo e vale
 *                  {@code 0} quando non sono presenti eventi pendenti.
 *
 * @see com.gameplatform.shared.dto.ServerListDto
 */
public record ServerHealthDto(
        String buildingId,
        String baseUrl,
        Instant lastSeenAt,
        boolean active,
        long pendingReplicationCount
) {}
