package com.gameplatform.central.domain.ports.in;

import java.util.List;

/**
 * Use case for assigning (binding) and revoking (unbinding) buildings to/from a
 * LOCAL_ADMIN user.
 *
 * <p>Operations are idempotent: assigning an already-bound building is a silent
 * no-op; revoking a non-existent binding is a silent no-op. Each effective
 * assign/revoke writes a corresponding outbox event
 * ({@code LOCAL_ADMIN_BUILDING_ASSIGNED} / {@code LOCAL_ADMIN_BUILDING_REVOKED})
 * so the change is replicated to every active Local Server.</p>
 */
public interface AssignLocalAdminBuildingsUseCase {
    void assignBuildings(String userId, List<String> buildingIds);
    void revokeBuildings(String userId, List<String> buildingIds);
}