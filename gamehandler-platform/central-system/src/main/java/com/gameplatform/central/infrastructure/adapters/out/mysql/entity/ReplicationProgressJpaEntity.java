package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "replication_progress", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"event_id", "server_id"})
})
public class ReplicationProgressJpaEntity {

    @Id
    @Column(name = "id", length = 100)
    private String id;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "server_id", nullable = false, length = 50)
    private String serverId;

    public ReplicationProgressJpaEntity() {
    }

    public ReplicationProgressJpaEntity(String id, String eventId, String serverId) {
        this.id = id;
        this.eventId = eventId;
        this.serverId = serverId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }
}
