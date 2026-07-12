package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary-key class for {@link LocalAdminBuildingJpaEntity}, matching
 * the PIANO SQL {@code PRIMARY KEY (user_id, building_id)}.
 *
 * <p>Field names MUST match the entity's {@code @Id} field names so Hibernate
 * can populate them via reflection.</p>
 */
public class LocalAdminBuildingId implements Serializable {
    private String userId;
    private String buildingId;

    public LocalAdminBuildingId() {
    }

    public LocalAdminBuildingId(String userId, String buildingId) {
        this.userId = userId;
        this.buildingId = buildingId;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocalAdminBuildingId that = (LocalAdminBuildingId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(buildingId, that.buildingId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, buildingId);
    }
}