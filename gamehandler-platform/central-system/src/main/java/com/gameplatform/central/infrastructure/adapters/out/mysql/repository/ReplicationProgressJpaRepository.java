package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.ReplicationProgressJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface ReplicationProgressJpaRepository extends JpaRepository<ReplicationProgressJpaEntity, String> {
    List<ReplicationProgressJpaEntity> findByEventId(String eventId);

    List<ReplicationProgressJpaEntity> findByEventIdInAndServerId(Collection<String> eventIds, String serverId);
}
