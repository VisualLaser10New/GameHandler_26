package com.gameplatform.local.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import com.gameplatform.local.domain.exception.InvalidGameStateTransitionException;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.domain.model.GameType;
import org.junit.jupiter.api.Test;

class GameTest {

    @Test
    void shouldCreateGameSuccessfully() {
        GameId gameId = new GameId("game-123");
        BuildingId buildingId = new BuildingId("building-1");
        Game game = new Game(gameId, GameType.CHESS, "Chess Table 1", buildingId, GameMachineStatus.AVAILABLE);

        assertEquals(gameId, game.getId());
        assertEquals(GameType.CHESS, game.getGameType());
        assertEquals("Chess Table 1", game.getName());
        assertEquals(buildingId, game.getBuildingId());
        assertEquals(GameMachineStatus.AVAILABLE, game.getStatus());
    }

    @Test
    void shouldAllowReservingAvailableGame() {
        Game game = createSampleGame(GameMachineStatus.AVAILABLE);
        game.reserve();
        assertEquals(GameMachineStatus.RESERVED, game.getStatus());
    }

    @Test
    void shouldFailReservingNonAvailableGame() {
        Game reservedGame = createSampleGame(GameMachineStatus.RESERVED);
        assertThrows(InvalidGameStateTransitionException.class, reservedGame::reserve);

        Game inUseGame = createSampleGame(GameMachineStatus.IN_USE);
        assertThrows(InvalidGameStateTransitionException.class, inUseGame::reserve);

        Game maintenanceGame = createSampleGame(GameMachineStatus.MAINTENANCE);
        assertThrows(InvalidGameStateTransitionException.class, maintenanceGame::reserve);
    }

    @Test
    void shouldAllowStartingUseFromAvailableOrReserved() {
        Game availableGame = createSampleGame(GameMachineStatus.AVAILABLE);
        availableGame.startUse();
        assertEquals(GameMachineStatus.IN_USE, availableGame.getStatus());

        Game reservedGame = createSampleGame(GameMachineStatus.RESERVED);
        reservedGame.startUse();
        assertEquals(GameMachineStatus.IN_USE, reservedGame.getStatus());
    }

    @Test
    void shouldFailStartingUseFromInUseOrMaintenance() {
        Game inUseGame = createSampleGame(GameMachineStatus.IN_USE);
        assertThrows(InvalidGameStateTransitionException.class, inUseGame::startUse);

        Game maintenanceGame = createSampleGame(GameMachineStatus.MAINTENANCE);
        assertThrows(InvalidGameStateTransitionException.class, maintenanceGame::startUse);
    }

    @Test
    void shouldAllowReleasingFromReservedInUseOrMaintenance() {
        Game reservedGame = createSampleGame(GameMachineStatus.RESERVED);
        reservedGame.release();
        assertEquals(GameMachineStatus.AVAILABLE, reservedGame.getStatus());

        Game inUseGame = createSampleGame(GameMachineStatus.IN_USE);
        inUseGame.release();
        assertEquals(GameMachineStatus.AVAILABLE, inUseGame.getStatus());

        Game maintenanceGame = createSampleGame(GameMachineStatus.MAINTENANCE);
        maintenanceGame.release();
        assertEquals(GameMachineStatus.AVAILABLE, maintenanceGame.getStatus());
    }

    @Test
    void shouldDoNothingWhenReleasingAlreadyAvailableGame() {
        Game availableGame = createSampleGame(GameMachineStatus.AVAILABLE);
        assertDoesNotThrow(availableGame::release);
        assertEquals(GameMachineStatus.AVAILABLE, availableGame.getStatus());
    }

    @Test
    void shouldAllowSettingMaintenanceFromAnyState() {
        Game availableGame = createSampleGame(GameMachineStatus.AVAILABLE);
        availableGame.setMaintenance();
        assertEquals(GameMachineStatus.MAINTENANCE, availableGame.getStatus());

        Game reservedGame = createSampleGame(GameMachineStatus.RESERVED);
        reservedGame.setMaintenance();
        assertEquals(GameMachineStatus.MAINTENANCE, reservedGame.getStatus());

        Game inUseGame = createSampleGame(GameMachineStatus.IN_USE);
        inUseGame.setMaintenance();
        assertEquals(GameMachineStatus.MAINTENANCE, inUseGame.getStatus());
    }

    private Game createSampleGame(GameMachineStatus status) {
        return new Game(
            new GameId("game-1"),
            GameType.CHESS,
            "Chess 1",
            new BuildingId("building-1"),
            status
        );
    }
}
