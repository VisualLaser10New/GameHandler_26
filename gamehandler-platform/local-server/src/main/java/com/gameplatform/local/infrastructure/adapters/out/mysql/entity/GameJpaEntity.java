package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import com.gameplatform.shared.domain.model.GameMachineStatus;
import jakarta.persistence.*;

@Entity
@Table(name = "game_catalog")
public class GameJpaEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "game_type", nullable = false, length = 50)
    private String gameType;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "building_id", nullable = false, length = 50)
    private String buildingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GameMachineStatus status;

    @Version
    @Column(name = "version", nullable = false, columnDefinition = "BIGINT NOT NULL DEFAULT 0")
    private Long version;

    public GameJpaEntity() {
    }

    public GameJpaEntity(String id, String gameType, String name, String buildingId, GameMachineStatus status) {
        this.id = id;
        this.gameType = gameType;
        this.name = name;
        this.buildingId = buildingId;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGameType() {
        return gameType;
    }

    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBuildingId() {
        return buildingId;
    }

    public void setBuildingId(String buildingId) {
        this.buildingId = buildingId;
    }

    public GameMachineStatus getStatus() {
        return status;
    }

    public void setStatus(GameMachineStatus status) {
        this.status = status;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
