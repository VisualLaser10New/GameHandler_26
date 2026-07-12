package com.gameplatform.local.domain.model;

import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.UserId;

import java.time.Instant;
import java.util.Objects;

public class LocalAdminBuilding {
    private final UserId userId;
    private final BuildingId buildingId;
    private final Instant assignedAt;

    public LocalAdminBuilding(UserId userId, BuildingId buildingId, Instant assignedAt) {
        if (userId == null) throw new IllegalArgumentException("UserId cannot be null");
        if (buildingId == null) throw new IllegalArgumentException("BuildingId cannot be null");
        if (assignedAt == null) throw new IllegalArgumentException("assignedAt cannot be null");
        this.userId = userId;
        this.buildingId = buildingId;
        this.assignedAt = assignedAt;
    }

    public UserId getUserId() { return userId; }
    public BuildingId getBuildingId() { return buildingId; }
    public Instant getAssignedAt() { return assignedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocalAdminBuilding that = (LocalAdminBuilding) o;
        return Objects.equals(userId, that.userId) && Objects.equals(buildingId, that.buildingId);
    }
    @Override
    public int hashCode() { return Objects.hash(userId, buildingId); }
}
