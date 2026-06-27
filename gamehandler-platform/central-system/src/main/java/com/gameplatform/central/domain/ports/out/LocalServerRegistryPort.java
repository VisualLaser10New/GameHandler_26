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
}

