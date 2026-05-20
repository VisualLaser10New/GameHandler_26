package com.gameplatform.central.domain.model;

import com.gameplatform.shared.domain.model.BuildingId;

import java.time.Instant;

public class RegisteredLocalServer {
    private BuildingId buildingId;
    private String baseUrl;
    private Instant lastSeenAt;
    private boolean isActive;

    public RegisteredLocalServer(BuildingId buildingId, String baseUrl, Instant lastSeenAt, boolean isActive) {
        this.buildingId = buildingId;
        this.baseUrl = baseUrl;
        this.lastSeenAt = lastSeenAt;
        this.isActive = isActive;
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
}
