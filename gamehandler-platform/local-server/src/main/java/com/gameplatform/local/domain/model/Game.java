package com.gameplatform.local.domain.model;

import com.gameplatform.local.domain.exception.InvalidGameStateTransitionException;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.domain.model.GameType;

public class Game {
    private final GameId id;
    private final GameType gameType;
    private String name;
    private final BuildingId buildingId;
    private GameMachineStatus status;
    private long version;

    public Game(GameId id, GameType gameType, String name, BuildingId buildingId, GameMachineStatus status) {
        this(id, gameType, name, buildingId, status, 0L);
    }

    public Game(GameId id, GameType gameType, String name, BuildingId buildingId, GameMachineStatus status, long version) {
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
        this.version = version;
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
        if (status != GameMachineStatus.AVAILABLE && status != GameMachineStatus.RESERVED && status != GameMachineStatus.LOBBY) {
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
        if (status != GameMachineStatus.IN_USE && status != GameMachineStatus.RESERVED && status != GameMachineStatus.MAINTENANCE && status != GameMachineStatus.LOBBY) {
            throw new InvalidGameStateTransitionException(
                "Cannot release game machine because its current status is: " + status
            );
        }
        this.status = GameMachineStatus.AVAILABLE;
    }

    public void setMaintenance() {
        this.status = GameMachineStatus.MAINTENANCE;
    }

    public void setLobby() {
        if (status != GameMachineStatus.AVAILABLE) {
            throw new InvalidGameStateTransitionException(
                "Cannot set game machine to LOBBY because its current status is: " + status
            );
        }
        this.status = GameMachineStatus.LOBBY;
    }

    public void rename(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("Name cannot be null, empty or blank");
        }
        this.name = newName;
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

    public long getVersion() {
        return version;
    }
}

