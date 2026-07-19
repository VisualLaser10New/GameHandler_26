package com.gameplatform.local.domain.model;

import com.gameplatform.shared.domain.model.BuildingId;

import java.time.Instant;
import java.util.Objects;

/**
 * Read-only local replica of a single registered local-server row
 * (PIANO §7.B), the flattened Central→Local projection of
 * {@code LOCAL_SERVER_REGISTRY_UPSERTED} events. Pure Java POJO,
 * immutable, identity = {@code buildingId} — mirror of the Central
 * {@code RegisteredLocalServer} model. Lets a PLATFORM_ADMIN connected
 * to any Local see the full registry of active/inactive servers
 * without a direct Central call (E1).
 */
public class RegisteredLocalServerLocal {

    private final BuildingId buildingId;
    private final String baseUrl;
    private final Instant lastSeenAt;
    private final boolean active;
    private final Instant updatedAt;

    /**
     * Costruisce una nuova replica locale di un server registrato.
     *
     * @param buildingId identificatore dell'edificio associato (non null)
     * @param baseUrl    URL di base del server (non blank)
     * @param lastSeenAt istante dell'ultimo contatto (può essere null)
     * @param active     true se il server è attivo
     * @param updatedAt  istante dell'ultimo aggiornamento (non null)
     * @throws IllegalArgumentException se buildingId è null, baseUrl è blank o updatedAt è null
     */
    public RegisteredLocalServerLocal(BuildingId buildingId, String baseUrl, Instant lastSeenAt,
                                      boolean active, Instant updatedAt) {
        if (buildingId == null) {
            throw new IllegalArgumentException("BuildingId cannot be null");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl cannot be blank");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("updatedAt cannot be null");
        }
        this.buildingId = buildingId;
        this.baseUrl = baseUrl;
        this.lastSeenAt = lastSeenAt;
        this.active = active;
        this.updatedAt = updatedAt;
    }

    /**
     * Restituisce l'identificatore dell'edificio associato.
     *
     * @return buildingId
     */
    public BuildingId getBuildingId() {
        return buildingId;
    }

    /**
     * Restituisce l'URL di base del server.
     *
     * @return baseUrl
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Restituisce l'istante dell'ultimo contatto con il server.
     *
     * @return lastSeenAt, o null se mai contattato
     */
    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    /**
     * Indica se il server è attualmente attivo.
     *
     * @return true se attivo
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Restituisce l'istante dell'ultimo aggiornamento.
     *
     * @return updatedAt
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Confronta questo server registrato con un altro oggetto per uguaglianza basata su buildingId.
     *
     * @param o l'oggetto da confrontare
     * @return true se gli oggetti sono uguali
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegisteredLocalServerLocal that = (RegisteredLocalServerLocal) o;
        return Objects.equals(buildingId, that.buildingId);
    }

    /**
     * Restituisce l'hash code basato su buildingId.
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(buildingId);
    }
}
