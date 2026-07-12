package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.model.OutboxEvent;
import com.gameplatform.local.domain.model.TournamentMatchLocal;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
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
 * Pure-Mockito slice tests for the FASE 6 tournament branch of
 * {@link SessionAbortHelper#abortAndEmit}. The helper is constructed with its
 * 7-arg constructor (concrete {@code TournamentMatchLocalRepository}) and a real
 * {@link ObjectMapper}. Per the FASE 6 coordinator decision Q2, an ABANDONED
 * tournament match yields a NON-NULL walkover winner so the central
 * {@code advanceWinner} keeps the tournament flowing.
 */
@ExtendWith(MockitoExtension.class)
class SessionAbortHelperTournamentTest {

    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");
    private static final String BUILDING_ID = "building-1";

    @Mock GameSessionRepository gameSessionRepository;
    @Mock GameRepository gameRepository;
    @Mock OutboxEventRepository outboxEventRepository;
    @Mock PublishGameStatePort publishGameStatePort;
    @Mock Clock clock;
    @Mock TournamentMatchLocalRepository tournamentMatchLocalRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private SessionAbortHelper helper;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(NOW);
        helper = new SessionAbortHelper(gameSessionRepository, gameRepository, outboxEventRepository,
                publishGameStatePort, clock, objectMapper, tournamentMatchLocalRepository);
    }

    private GameSession inProgressTournamentSession() {
        return new GameSession(new GameSessionId("s-1"), new GameId("game-1"), GameType.CHESS,
                new BuildingId(BUILDING_ID), GameStatus.IN_PROGRESS, NOW, null, null, null, null, null,
                List.of(new UserId("u1"), new UserId("u2")),
                new TournamentMatchId("m-1"), new TournamentId("t-1"));
    }

    private Game inUseGame() {
        return new Game(new GameId("game-1"), GameType.CHESS, "Chess 1",
                new BuildingId(BUILDING_ID), GameMachineStatus.IN_USE);
    }

    private TournamentMatchLocal scheduledLocalMatch() {
        return new TournamentMatchLocal(new TournamentMatchId("m-1"), new TournamentId("t-1"),
                1, 1, "u1", "u2", GameType.CHESS, "game-1",
                TournamentMatchStatus.SCHEDULED, NOW);
    }

    @Test
    void abortAndEmit_withTournamentMatchId_writesAbandonedTournamentOutboxRow() throws Exception {
        GameSession s = inProgressTournamentSession();
        when(gameRepository.findById(any())).thenReturn(Optional.of(inUseGame()));
        when(tournamentMatchLocalRepository.findById(eq(new TournamentMatchId("m-1"))))
                .thenReturn(Optional.of(scheduledLocalMatch()));

        helper.abortAndEmit(s, StopReason.TIMEOUT, "TIMEOUT");

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, times(2)).save(outboxCaptor.capture());
        assertEquals("GAME_SESSION_ABORTED", outboxCaptor.getAllValues().get(0).getEventType());
        OutboxEvent tournamentEvent = outboxCaptor.getAllValues().get(1);
        assertEquals("TOURNAMENT_MATCH_COMPLETED", tournamentEvent.getEventType());
        // Q2: the ABANDONED row carries a NON-NULL walkover winner (participantB = "u2").
        String payload = tournamentEvent.getPayload();
        assertTrue(payload.contains("\"status\":\"ABANDONED\""));
        assertTrue(payload.contains("\"winner\":\"u2\""));

        ArgumentCaptor<TournamentMatchLocal> matchCaptor = ArgumentCaptor.forClass(TournamentMatchLocal.class);
        verify(tournamentMatchLocalRepository).save(matchCaptor.capture());
        assertEquals(TournamentMatchStatus.ABANDONED, matchCaptor.getValue().getStatus());
    }

    @Test
    void abortAndEmit_withoutTournamentMatchId_writesOnlyAbortedRow() throws Exception {
        GameSession s = new GameSession(new GameSessionId("s-1"), new GameId("game-1"), GameType.CHESS,
                new BuildingId(BUILDING_ID), GameStatus.IN_PROGRESS, NOW, null, null, null, null, null,
                List.of(new UserId("u1"), new UserId("u2")));
        when(gameRepository.findById(any())).thenReturn(Optional.of(inUseGame()));

        helper.abortAndEmit(s, StopReason.TIMEOUT, "TIMEOUT");

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertEquals("GAME_SESSION_ABORTED", outboxCaptor.getValue().getEventType());
        verify(tournamentMatchLocalRepository, never()).save(any());
        verify(tournamentMatchLocalRepository, never()).findById(any());
    }
}