package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "local_servers")
public class RegisteredLocalServerJpaEntity {

    @Id
    @Column(name = "building_id", length = 50)
    private String buildingId;

    @Column(name = "base_url", nullable = false, length = 255)
    private String baseUrl;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    public RegisteredLocalServerJpaEntity() {
    }

    public RegisteredLocalServerJpaEntity(String buildingId, String baseUrl, Instant lastSeenAt, boolean isActive) {
        this.buildingId = buildingId;
        this.baseUrl = baseUrl;
        this.lastSeenAt = lastSeenAt;
        this.isActive = isActive;
    }

    public String getBuildingId() {
        return buildingId;
    }

    public void setBuildingId(String buildingId) {
        this.buildingId = buildingId;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }
}
