package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gameplatform.local.domain.exception.GameNotAvailableException;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.domain.model.GameType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GameStateServiceTest {

    @Mock GameRepository gameRepository;
    @Mock PublishGameStatePort publishGameStatePort;

    @InjectMocks GameStateService service;

    private Game game(GameMachineStatus status) {
        return new Game(new GameId("game-1"), GameType.CHESS, "Chess 1", new BuildingId("b-1"), status);
    }

    @Test
    void shouldReleaseGameWhenUpdatingToAvailable() {
        Game g = game(GameMachineStatus.IN_USE);
        when(gameRepository.findById(any())).thenReturn(Optional.of(g));
        service.updateState(new GameId("game-1"), GameMachineStatus.AVAILABLE);
        assertEquals(GameMachineStatus.AVAILABLE, g.getStatus());
        verify(gameRepository).save(g);
        verify(publishGameStatePort).publishState(new GameId("game-1"), GameMachineStatus.AVAILABLE);
    }

    @Test
    void shouldReserveGameWhenUpdatingToReserved() {
        Game g = game(GameMachineStatus.AVAILABLE);
        when(gameRepository.findById(any())).thenReturn(Optional.of(g));
        service.updateState(new GameId("game-1"), GameMachineStatus.RESERVED);
        assertEquals(GameMachineStatus.RESERVED, g.getStatus());
        verify(publishGameStatePort).publishState(new GameId("game-1"), GameMachineStatus.RESERVED);
    }

    @Test
    void shouldStartUseWhenUpdatingToInUse() {
        Game g = game(GameMachineStatus.AVAILABLE);
        when(gameRepository.findById(any())).thenReturn(Optional.of(g));
        service.updateState(new GameId("game-1"), GameMachineStatus.IN_USE);
        assertEquals(GameMachineStatus.IN_USE, g.getStatus());
    }

    @Test
    void shouldSetMaintenanceWhenUpdatingToMaintenance() {
        Game g = game(GameMachineStatus.IN_USE);
        when(gameRepository.findById(any())).thenReturn(Optional.of(g));
        service.updateState(new GameId("game-1"), GameMachineStatus.MAINTENANCE);
        assertEquals(GameMachineStatus.MAINTENANCE, g.getStatus());
    }

    @Test
    void shouldNotSaveOrPublishWhenStatusIsUnchanged() {
        // Guards against the MQTT echo loop: the local-server subscribes to the
        // same state topic it publishes to, so an idempotent updateState must
        // not re-save/re-publish when the status did not actually change.
        Game g = game(GameMachineStatus.AVAILABLE);
        when(gameRepository.findById(any())).thenReturn(Optional.of(g));
        service.updateState(new GameId("game-1"), GameMachineStatus.AVAILABLE);
        assertEquals(GameMachineStatus.AVAILABLE, g.getStatus());
        verify(gameRepository, never()).save(any());
        verify(publishGameStatePort, never()).publishState(any(), any());
    }

    @Test
    void shouldFailUpdateWhenGameNotFound() {
        when(gameRepository.findById(any())).thenReturn(Optional.empty());
        assertThrows(GameNotAvailableException.class, () ->
                service.updateState(new GameId("nope"), GameMachineStatus.AVAILABLE));
        verify(publishGameStatePort, never()).publishState(any(), any());
    }

    @Test
    void shouldReturnAvailableGames() {
        when(gameRepository.findByStatus(GameMachineStatus.AVAILABLE)).thenReturn(List.of(game(GameMachineStatus.AVAILABLE)));
        assertEquals(1, service.getAvailable().size());
        verify(gameRepository).findByStatus(GameMachineStatus.AVAILABLE);
    }

    @Test
    void shouldReturnAllGames() {
        when(gameRepository.findAll()).thenReturn(List.of(game(GameMachineStatus.AVAILABLE)));
        assertEquals(1, service.getAll().size());
        verify(gameRepository).findAll();
    }
}
