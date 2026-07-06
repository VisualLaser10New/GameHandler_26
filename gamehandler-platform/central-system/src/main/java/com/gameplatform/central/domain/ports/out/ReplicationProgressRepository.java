package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.ReplicationProgress;
import java.util.List;

public interface ReplicationProgressRepository {
    List<ReplicationProgress> findByEventId(String eventId);
    void save(ReplicationProgress progress);
    boolean existsByEventIdAndServerId(String eventId, String serverId);
}
