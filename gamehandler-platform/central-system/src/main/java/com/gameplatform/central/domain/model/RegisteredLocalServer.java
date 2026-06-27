package com.gameplatform.central.domain.model;

import com.gameplatform.shared.domain.model.BuildingId;

import java.time.Instant;
import java.util.Objects;

public class RegisteredLocalServer {
    private BuildingId buildingId;
    private String baseUrl;
    private Instant lastSeenAt;
    private boolean isActive;

    public RegisteredLocalServer(BuildingId buildingId, String baseUrl, Instant lastSeenAt, boolean isActive) {
        if (buildingId == null) {
            throw new IllegalArgumentException("BuildingId cannot be null");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Base URL cannot be null, empty or blank");
        }
        this.buildingId = buildingId;
        this.baseUrl = baseUrl;
        this.lastSeenAt = lastSeenAt;
        this.isActive = isActive;
    }

    public void updateLastSeen(Instant lastSeenAt) {
        if (lastSeenAt == null) {
            throw new IllegalArgumentException("lastSeenAt cannot be null");
        }
        this.lastSeenAt = lastSeenAt;
    }

    public void setActive(boolean active) {
        this.isActive = active;
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
        return isActive;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegisteredLocalServer that = (RegisteredLocalServer) o;
        return Objects.equals(buildingId, that.buildingId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(buildingId);
    }
}

