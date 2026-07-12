package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "local_admin_buildings_local")
@IdClass(LocalAdminBuildingId.class)
public class LocalAdminBuildingJpaEntity {
    @Id
    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;
    @Id
    @Column(name = "building_id", length = 100, nullable = false)
    private String buildingId;
    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    public LocalAdminBuildingJpaEntity() {
    }

    public LocalAdminBuildingJpaEntity(String userId, String buildingId, Instant assignedAt) {
        this.userId = userId;
        this.buildingId = buildingId;
        this.assignedAt = assignedAt;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getBuildingId() {
        return buildingId;
    }

    public void setBuildingId(String buildingId) {
        this.buildingId = buildingId;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(Instant assignedAt) {
        this.assignedAt = assignedAt;
    }
}
