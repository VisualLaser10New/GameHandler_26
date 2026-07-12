package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.LocalAdminBuilding;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.UserId;

import java.util.List;

/**
 * Persistence port for the {@code local_admin_buildings} binding table.
 *
 * <p>The table is the central Source-of-Truth for the LOCAL_ADMIN &harr;
 * building relation. Persisted rows are upserted/deleted by composite PK
 * {@code (user_id, building_id)} so callers are naturally idempotent.</p>
 */
public interface LocalAdminBuildingRepository {
    /** Upserts the binding (composite PK). */
    LocalAdminBuilding save(LocalAdminBuilding binding);

    /** True iff the (userId, buildingId) binding currently exists. */
    boolean existsByUserIdAndBuildingId(UserId userId, BuildingId buildingId);

    /** Deletes the (userId, buildingId) binding if it exists (no-op otherwise). */
    void deleteByUserIdAndBuildingId(UserId userId, BuildingId buildingId);

    /** All bindings for the given user (empty list if none). */
    List<LocalAdminBuilding> findByUserId(UserId userId);
}