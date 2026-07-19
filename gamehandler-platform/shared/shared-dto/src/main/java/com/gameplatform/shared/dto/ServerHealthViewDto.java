package com.gameplatform.shared.dto;

import java.time.Instant;
import java.util.List;

/**
 * Vista aggregata dello stato di salute del server Locale, restituita
 * dall'endpoint {@code GET /api/admin/servers/health} riservato al
 * PLATFORM_ADMIN (PIANO §7.B).
 *
 * <p>Combina il conteggio delle righe in stato PENDING presenti
 * sull'outbox del nodo Locale che risponde con il registro di tutti i
 * server Locali conosciuti, replicato tramite l'evento
 * {@code LOCAL_SERVER_REGISTRY_UPSERTED} nella tabella
 * {@code registered_local_servers_local}.</p>
 *
 * @param myBuildingId           identificativo dell'edificio del nodo Locale che risponde; puo' essere {@code null} se il nodo non e' ancora stato associato a un edificio
 * @param myServerActive         indica se il nodo Locale che risponde risulta attivo
 * @param myLastSeenAt           istante dell'ultimo heartbeat ricevuto dal nodo Locale che risponde; puo' essere {@code null} se non e' mai stato registrato un heartbeat
 * @param myPendingOutboxCount   numero di righe in stato PENDING presenti sull'outbox del nodo Locale; e' pari a {@code 0} quando non ci sono messaggi in attesa
 * @param registeredServers      elenco completo dei server Locali registrati; e' una lista vuota e non {@code null} quando non risulta registrato alcun server
 *
 * @see com.gameplatform.shared.dto.ServerHealthDto
 */
public record ServerHealthViewDto(
        String myBuildingId,
        boolean myServerActive,
        Instant myLastSeenAt,
        long myPendingOutboxCount,
        List<ServerHealthDto> registeredServers
) {
}
