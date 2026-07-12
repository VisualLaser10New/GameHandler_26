package com.gameplatform.central.domain.model;

import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain entity representing the binding of a LOCAL_ADMIN user to a building.
 *
 * <p>This is the central Source-of-Truth record: which administrator owns which
 * building. Bindings are replicated to the Local Servers via the outbox events
 * {@code LOCAL_ADMIN_BUILDING_ASSIGNED} / {@code LOCAL_ADMIN_BUILDING_REVOKED}.</p>
 *
 * <p>Identity is the composite (userId, buildingId) pair — a user may administer
 * more than one building and a building may have more than one administrator.</p>
 */
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

    public UserId getUserId() {
        return userId;
    }

    public BuildingId getBuildingId() {
        return buildingId;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocalAdminBuilding that = (LocalAdminBuilding) o;
        return Objects.equals(userId, that.userId) && Objects.equals(buildingId, that.buildingId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, buildingId);
    }
}