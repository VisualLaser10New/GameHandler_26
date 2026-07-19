package com.gameplatform.central.domain.model;

/**
 * Record immutabile che rappresenta l'avanzamento della replica di un evento
 * verso un determinato server, associando l'identificativo dell'evento a quello
 * del server che lo ha ricevuto. Consente di tracciare quali eventi sono già
 * stati replicati su ciascun server.
 *
 * @param eventId identificativo dell'evento replicato; non può essere {@code null} né vuoto
 * @param serverId identificativo del server destinatario; non può essere {@code null} né vuoto
 */
public record ReplicationProgress(String eventId, String serverId) {
    /**
     * Costruttore compatto che valida i componenti del record.
     *
     * @throws IllegalArgumentException se {@code eventId} o {@code serverId} è {@code null} o vuoto
     */
    public ReplicationProgress {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId cannot be null or blank");
        }
        if (serverId == null || serverId.isBlank()) {
            throw new IllegalArgumentException("serverId cannot be null or blank");
        }
    }
}
