package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.shared.domain.model.BuildingId;
import java.time.Instant;
import java.util.List;

public interface LocalServerRegistryPort {
    List<RegisteredLocalServer> getActiveLocalServers();
    void register(RegisteredLocalServer server);
    /** Updates the lastSeenAt timestamp for the server identified by the given buildingId. */
    void updateLastSeenAt(BuildingId buildingId, Instant lastSeenAt);

    /**
     * M12 — returns ALL registered local servers (active and inactive), newest
     * {@code lastSeenAt} first. Used by the admin {@code /internal/servers}
     * endpoint to build the per-server health view.
     */
    List<RegisteredLocalServer> findAll();

    /**
     * M13 — atomically deactivates the local server identified by the given
     * building. Once deactivated, the server is no longer returned by
     * {@link #getActiveLocalServers()} so the replication scheduler stops
     * pushing to it. Re-registration with {@code register(...)} flips it
     * back to active.
     */
    void deactivate(BuildingId buildingId);
}

