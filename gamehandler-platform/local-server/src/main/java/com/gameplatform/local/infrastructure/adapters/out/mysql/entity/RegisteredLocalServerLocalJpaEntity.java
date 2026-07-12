package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity for {@code registered_local_servers_local} (PIANO §7.B).
 * Read-only replica updated only by sync; PK {@code building_id}; no
 * {@code @OneToMany}, no {@code @Version} (mirror of
 * {@code GameDefinitionLocalJpaEntity}).
 */
@Entity
@Table(name = "registered_local_servers_local")
public class RegisteredLocalServerLocalJpaEntity {

    @Id
    @Column(name = "building_id", length = 64, nullable = false)
    private String buildingId;

    @Column(name = "base_url", length = 255, nullable = false)
    private String baseUrl;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public RegisteredLocalServerLocalJpaEntity() {
    }

    public RegisteredLocalServerLocalJpaEntity(String buildingId, String baseUrl, Instant lastSeenAt,
                                               Boolean active, Instant updatedAt) {
        this.buildingId = buildingId;
        this.baseUrl = baseUrl;
        this.lastSeenAt = lastSeenAt;
        this.active = active;
        this.updatedAt = updatedAt;
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

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
