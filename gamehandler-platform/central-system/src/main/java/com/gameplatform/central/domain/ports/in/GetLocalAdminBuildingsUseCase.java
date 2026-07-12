package com.gameplatform.central.domain.ports.in;

import java.util.List;

/**
 * Use case for querying the buildings bound to a LOCAL_ADMIN user.
 */
public interface GetLocalAdminBuildingsUseCase {
    /** Returns the building ids currently bound to the given user (empty if none). */
    List<String> getBuildingsForUser(String userId);
}