package com.gameplatform.local.domain.model;

import com.gameplatform.local.domain.exception.InvalidGameStateTransitionException;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.domain.model.GameType;

public class Game {
    private final GameId id;
    private final GameType gameType;
    private final String name;
    private final BuildingId buildingId;
    private GameMachineStatus status;

    public Game(GameId id, GameType gameType, String name, BuildingId buildingId, GameMachineStatus status) {
        if (id == null) {
            throw new IllegalArgumentException("GameId cannot be null");
        }
        if (gameType == null) {
            throw new IllegalArgumentException("GameType cannot be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        if (buildingId == null) {
            throw new IllegalArgumentException("BuildingId cannot be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("GameMachineStatus cannot be null");
        }
        this.id = id;
        this.gameType = gameType;
        this.name = name;
        this.buildingId = buildingId;
        this.status = status;
    }

    public void reserve() {
        if (status != GameMachineStatus.AVAILABLE) {
            throw new InvalidGameStateTransitionException(
                "Cannot reserve game machine because its current status is: " + status
            );
        }
        this.status = GameMachineStatus.RESERVED;
    }

    public void startUse() {
        if (status != GameMachineStatus.AVAILABLE && status != GameMachineStatus.RESERVED) {
            throw new InvalidGameStateTransitionException(
                "Cannot start using game machine because its current status is: " + status
            );
        }
        this.status = GameMachineStatus.IN_USE;
    }

    public void release() {
        if (status == GameMachineStatus.AVAILABLE) {
            return; // Already available
        }
        if (status != GameMachineStatus.IN_USE && status != GameMachineStatus.RESERVED && status != GameMachineStatus.MAINTENANCE) {
            throw new InvalidGameStateTransitionException(
                "Cannot release game machine because its current status is: " + status
            );
        }
        this.status = GameMachineStatus.AVAILABLE;
    }

    public void setMaintenance() {
        this.status = GameMachineStatus.MAINTENANCE;
    }

    public GameId getId() {
        return id;
    }

    public GameType getGameType() {
        return gameType;
    }

    public String getName() {
        return name;
    }

    public BuildingId getBuildingId() {
        return buildingId;
    }

    public GameMachineStatus getStatus() {
        return status;
    }
}

