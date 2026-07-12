package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gameplatform.local.domain.exception.TournamentMatchNotFoundException;
import com.gameplatform.local.domain.exception.TournamentMatchNotScheduledException;
import com.gameplatform.local.domain.exception.TournamentMatchValidationException;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameDefinitionLocal;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.model.OutboxEvent;
import com.gameplatform.local.domain.model.TournamentMatchLocal;
import com.gameplatform.local.domain.ports.out.GameDefinitionLocalRepository;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.local.domain.ports.out.ReservationRepository;
import com.gameplatform.local.domain.ports.out.TournamentMatchLocalRepository;
import com.gameplatform.shared.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Pure-Mockito slice tests for the FASE 6 tournament-aware overloads of
 * {@link GameSessionService}: the 5-arg {@code start(...)} and the tournament
 * branch of {@code end(...)}. The service is constructed with its 10-arg
 * constructor (concrete {@code TournamentMatchLocalRepository} + literal
 * {@code buildingId = "building-1"}) and a real {@link ObjectMapper}.
 */
@ExtendWith(MockitoExtension.class)
class GameSessionServiceTournamentTest {

    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");
    private static final String BUILDING_ID = "building-1";

    @Mock GameSessionRepository gameSessionRepository;
    @Mock GameRepository gameRepository;
    @Mock OutboxEventRepository outboxEventRepository;
    @Mock PublishGameStatePort publishGameStatePort;
    @Mock ReservationRepository reservationRepository;
    @Mock GameDefinitionLocalRepository gameDefinitionLocalRepository;
    @Mock TournamentMatchLocalRepository tournamentMatchLocalRepository;
    @Mock Clock clock;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private GameSessionService service;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(NOW);
        service = new GameSessionService(
                gameSessionRepository, gameRepository, outboxEventRepository,
                publishGameStatePort, reservationRepository, clock, objectMapper,
                gameDefinitionLocalRepository, tournamentMatchLocalRepository, BUILDING_ID);
    }

    private TournamentMatchLocal scheduledLocalMatch() {
        return new TournamentMatchLocal(new TournamentMatchId("m-1"), new TournamentId("t-1"),
                1, 1, "u1", "u2", GameType.CHESS, "game-1",
                TournamentMatchStatus.SCHEDULED, NOW);
    }

    private Game availableGame() {
        return new Game(new GameId("game-1"), GameType.CHESS, "Chess 1",
                new BuildingId(BUILDING_ID), GameMachineStatus.AVAILABLE);
    }

    private GameDefinitionLocal individualDef() {
        return new GameDefinitionLocal(GameType.CHESS, "Chess", 2, 2, false, null, NOW);
    }

    // ── start(...) 5-arg overload ──

    @Test
    void start_withTournamentMatchId_loadsLocalMatchAndFlipsToInProgress() {
        when(tournamentMatchLocalRepository.findById(eq(new TournamentMatchId("m-1"))))
                .thenReturn(Optional.of(scheduledLocalMatch()));
        when(gameDefinitionLocalRepository.findByGameType(any())).thenReturn(Optional.of(individualDef()));
        when(gameSessionRepository.findActiveByGameId(any())).thenReturn(Optional.empty());
        when(gameRepository.findByIdForUpdate(any())).thenReturn(Optional.of(availableGame()));
        when(gameSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GameSession s = service.start(new GameId("game-1"), GameType.CHESS,
                List.of(new UserId("u1"), new UserId("u2")), null, new TournamentMatchId("m-1"));

        ArgumentCaptor<TournamentMatchLocal> matchCaptor = ArgumentCaptor.forClass(TournamentMatchLocal.class);
        verify(tournamentMatchLocalRepository).save(matchCaptor.capture());
        assertEquals(TournamentMatchStatus.IN_PROGRESS, matchCaptor.getValue().getStatus());
        assertEquals(new TournamentMatchId("m-1"), s.getTournamentMatchId());
        assertEquals(new TournamentId("t-1"), s.getTournamentId());
        verify(gameRepository).save(any());
    }

    @Test
    void start_withTournamentMatchId_throwsWhenMatchNotScheduled() {
        TournamentMatchLocal completed = scheduledLocalMatch().withStatus(TournamentMatchStatus.COMPLETED);
        when(tournamentMatchLocalRepository.findById(eq(new TournamentMatchId("m-1"))))
                .thenReturn(Optional.of(completed));

        assertThrows(TournamentMatchNotScheduledException.class, () ->
                service.start(new GameId("game-1"), GameType.CHESS,
                        List.of(new UserId("u1"), new UserId("u2")), null, new TournamentMatchId("m-1")));

        verify(gameSessionRepository, never()).save(any());
    }

    @Test
    void start_withTournamentMatchId_throwsWhenMatchMissing() {
        when(tournamentMatchLocalRepository.findById(eq(new TournamentMatchId("m-1"))))
                .thenReturn(Optional.empty());

        assertThrows(TournamentMatchNotFoundException.class, () ->
                service.start(new GameId("game-1"), GameType.CHESS,
                        List.of(new UserId("u1"), new UserId("u2")), null, new TournamentMatchId("m-1")));
    }

    @Test
    void start_teamAllowedMismatch_throwsValidationException() {
        TournamentMatchLocal teamMatch = new TournamentMatchLocal(new TournamentMatchId("m-1"),
                new TournamentId("t-1"), 1, 1, "team-1", null, GameType.CHESS, "game-1",
                TournamentMatchStatus.SCHEDULED, NOW);
        when(tournamentMatchLocalRepository.findById(eq(new TournamentMatchId("m-1"))))
                .thenReturn(Optional.of(teamMatch));
        GameDefinitionLocal teamDef = new GameDefinitionLocal(GameType.CHESS, "Chess", 1, 4, true, null, NOW);
        when(gameDefinitionLocalRepository.findByGameType(any())).thenReturn(Optional.of(teamDef));

        // Team definition but 2 user participants instead of 1 pseudo-participant → mismatch.
        assertThrows(TournamentMatchValidationException.class, () ->
                service.start(new GameId("game-1"), GameType.CHESS,
                        List.of(new UserId("u1"), new UserId("u2")), null, new TournamentMatchId("m-1")));

        verify(gameSessionRepository, never()).save(any());
    }

    // ── end(...) tournament branch ──

    private GameSession tournamentSession() {
        return new GameSession(new GameSessionId("s-1"), new GameId("game-1"), GameType.CHESS,
                new BuildingId(BUILDING_ID), GameStatus.IN_PROGRESS, NOW, null, null, null, null, null,
                List.of(new UserId("u1"), new UserId("u2")), new TournamentMatchId("m-1"), new TournamentId("t-1"));
    }

    private Game inUseGame() {
        return new Game(new GameId("game-1"), GameType.CHESS, "Chess 1",
                new BuildingId(BUILDING_ID), GameMachineStatus.IN_USE);
    }

    @Test
    void end_withTournamentMatchId_writesTwoOutboxRowsAndCompletesLocalMatch() {
        GameSession s = tournamentSession();
        when(gameSessionRepository.findById(any())).thenReturn(Optional.of(s));
        when(gameRepository.findById(any())).thenReturn(Optional.of(inUseGame()));
        when(tournamentMatchLocalRepository.findById(eq(new TournamentMatchId("m-1"))))
                .thenReturn(Optional.of(scheduledLocalMatch()));

        service.end(new GameSessionId("s-1"), null);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, times(2)).save(outboxCaptor.capture());
        assertEquals("GAME_SESSION_COMPLETED", outboxCaptor.getAllValues().get(0).getEventType());
        OutboxEvent tournamentEvent = outboxCaptor.getAllValues().get(1);
        assertEquals("TOURNAMENT_MATCH_COMPLETED", tournamentEvent.getEventType());
        assertTrue(tournamentEvent.getPayload().contains("\"status\":\"COMPLETED\""));

        ArgumentCaptor<TournamentMatchLocal> matchCaptor = ArgumentCaptor.forClass(TournamentMatchLocal.class);
        verify(tournamentMatchLocalRepository).save(matchCaptor.capture());
        assertEquals(TournamentMatchStatus.COMPLETED, matchCaptor.getValue().getStatus());
    }

    @Test
    void end_withoutTournamentMatchId_writesOnlyOneOutboxRow() {
        GameSession s = new GameSession(new GameSessionId("s-1"), new GameId("game-1"), GameType.CHESS,
                new BuildingId(BUILDING_ID), GameStatus.IN_PROGRESS, NOW, null, null, null, null, null,
                List.of(new UserId("u1"), new UserId("u2")));
        when(gameSessionRepository.findById(any())).thenReturn(Optional.of(s));
        when(gameRepository.findById(any())).thenReturn(Optional.of(inUseGame()));

        service.end(new GameSessionId("s-1"), null);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertEquals("GAME_SESSION_COMPLETED", outboxCaptor.getValue().getEventType());
        verify(tournamentMatchLocalRepository, never()).save(any());
    }
}