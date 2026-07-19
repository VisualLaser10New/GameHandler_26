package com.gameplatform.shared.dto;

import java.time.Instant;

/**
 * Outbox payload for the {@code LOCAL_SERVER_REGISTRY_UPSERTED} event in the
 * Central→Local replication flow (PIANO §7.A.3). Carries a single registered
 * local server row so the local node can upsert its
 * {@code registered_local_servers_local} projection (idempotent by PK
 * {@code buildingId}). This lets a PLATFORM_ADMIN client, connected to any
 * Local, see the full registry of active/inactive servers without a direct
 * Central call (E1).
 *
 * <p>{@code originatingRequestId} is nullable: registry events are raised by
 * the Central {@code LocalServerRegistryPort.register} path, where it is
 * {@code null} (no admin request is being closed).</p>
 *
 * @param eventId              outbox event id (UUID)
 * @param eventType            always {@code LOCAL_SERVER_REGISTRY_UPSERTED}
 * @param buildingId           the server building id (PK)
 * @param baseUrl              the server base URL
 * @param lastSeenAt           the last heartbeat instant
 * @param active               whether the server is currently active
 * @param originatingRequestId id of the originating request/event (nullable)
 * @param updatedAt            last mutation instant
 *
 * @see LocalServerRegistryEventDto#LocalServerRegistryEventDto(String, String, String, String, Instant, boolean, String, Instant)
 */
public record LocalServerRegistryEventDto(
        String eventId,
        String eventType,
        String buildingId,
        String baseUrl,
        Instant lastSeenAt,
        boolean active,
        String originatingRequestId,
        Instant updatedAt
) {
    /**
     * Costruisce un evento di registro server locale impostando a {@code null}
     * l'identificativo della richiesta origine.
     *
     * <p>Questo costruttore di convenienza delega al costruttore completo
     * valorizzando {@code originatingRequestId} a {@code null}, in quanto gli
     * eventi di registro sollevati dal percorso
     * {@code LocalServerRegistryPort.register} non chiudono alcuna richiesta
     * amministrativa.</p>
     *
     * @param eventId    identificativo dell'evento outbox (UUID); non deve essere {@code null}
     * @param eventType  tipo di evento, sempre {@code LOCAL_SERVER_REGISTRY_UPSERTED}; non deve essere {@code null}
     * @param buildingId identificativo della sede del server, chiave primaria; non deve essere {@code null} o vuoto
     * @param baseUrl    URL di base del server; non deve essere {@code null} o vuoto
     * @param lastSeenAt istante dell'ultimo heartbeat ricevuto; non deve essere {@code null}
     * @param active     {@code true} se il server è attualmente attivo, {@code false} altrimenti
     * @param updatedAt  istante dell'ultima mutazione; non deve essere {@code null}
     *
     * @throws NullPointerException se uno qualsiasi dei parametri non nullable è {@code null}
     *
     * @see LocalServerRegistryEventDto#LocalServerRegistryEventDto(String, String, String, String, Instant, boolean, String, Instant)
     */
    public LocalServerRegistryEventDto(String eventId, String eventType, String buildingId,
                                       String baseUrl, Instant lastSeenAt, boolean active, Instant updatedAt) {
        this(eventId, eventType, buildingId, baseUrl, lastSeenAt, active, null, updatedAt);
    }
}