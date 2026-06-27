package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionRecoveryServiceTest {

    @Mock GameSessionRepository gameSessionRepository;
    @Mock PublishGameStatePort publishGameStatePort;
    @Mock SessionRecoveryHelper sessionRecoveryHelper;

    @InjectMocks SessionRecoveryService service;

    private GameSession session(GameId gameId, GameStatus status) {
        return new GameSession(new GameSessionId("s-1"), gameId, GameType.CHESS, new BuildingId("b-1"),
                status, Instant.parse("2026-06-01T10:00:00Z"), null, null, null, null, null, List.of(new UserId("u-1")));
    }

    private void awaitRunning(boolean expected) throws Exception {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            if (service.isRunning() == expected) return;
            Thread.sleep(20);
        }
        assertEquals(expected, service.isRunning());
    }

    @Test
    void startWithNoActiveSessionsShouldCompleteRecoveryAndStopRunning() throws Exception {
        when(gameSessionRepository.findByStatus(GameStatus.IN_PROGRESS)).thenReturn(List.of());
        when(gameSessionRepository.findByStatus(GameStatus.PAUSED)).thenReturn(List.of());

        service.start();

        verify(gameSessionRepository, timeout(2000)).findByStatus(GameStatus.IN_PROGRESS);
        verify(gameSessionRepository, timeout(2000)).findByStatus(GameStatus.PAUSED);
        awaitRunning(false);
        verify(publishGameStatePort, never()).publishSessionEvent(anyString(), any());
    }

    @Test
    void shouldReportNotRunningInitially() {
        assertFalse(service.isRunning());
    }

    @Test
    void shouldBeAutoStartupWithMaxPhase() {
        assertTrue(service.isAutoStartup());
        assertEquals(Integer.MAX_VALUE, service.getPhase());
    }

    @Test
    void stopShouldMarkNotRunning() {
        service.stop();
        assertFalse(service.isRunning());
    }

    @Test
    void registerHeartbeatAckForUnknownGameShouldBeSafe() {
        assertDoesNotThrow(() -> service.registerHeartbeatAck(new GameId("unknown")));
    }

    @Test
    void stopShouldInterruptSleepAndHaltImmediately() throws Exception {
        GameSession session = session(new GameId("g-1"), GameStatus.IN_PROGRESS);
        lenient().when(gameSessionRepository.findByStatus(GameStatus.IN_PROGRESS)).thenReturn(List.of(session));
        lenient().when(gameSessionRepository.findByStatus(GameStatus.PAUSED)).thenReturn(List.of());

        long startTime = System.currentTimeMillis();
        service.start();
        
        // Wait a tiny bit for the thread to start and go to sleep
        awaitRunning(true);
        
        // Stop the service (which should interrupt the thread)
        service.stop();
        
        // Wait for the thread to terminate (not running)
        awaitRunning(false);
        
        long duration = System.currentTimeMillis() - startTime;
        // The thread normally sleeps for 30,000ms. If it was interrupted, it will halt in < 2,000ms.
        assertTrue(duration < 2000, "Thread should have halted immediately, but took " + duration + "ms");
    }
}
