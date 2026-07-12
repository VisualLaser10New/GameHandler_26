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

    public BuildingId getBuildingId() {
        return buildingId;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegisteredLocalServerLocal that = (RegisteredLocalServerLocal) o;
        return Objects.equals(buildingId, that.buildingId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(buildingId);
    }
}
