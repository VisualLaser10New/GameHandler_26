package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gameplatform.local.domain.model.TournamentMatchLocal;
import com.gameplatform.local.domain.ports.out.TournamentMatchLocalRepository;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import com.gameplatform.shared.domain.model.TournamentMatchStatus;
import com.gameplatform.shared.dto.TournamentMatchScheduledDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Pure-Mockito unit tests for {@link TournamentMatchLocalSyncService}, covering
 * FASE 6 idempotent upsert of replicated {@code TOURNAMENT_MATCH_SCHEDULED}
 * events into the {@code tournament_matches_local} table. Mirrors the
 * {@code GameDefinitionSyncService} test convention.
 */
@ExtendWith(MockitoExtension.class)
class TournamentMatchLocalSyncServiceTest {

    private static final Instant SCHEDULED_AT = Instant.parse("2026-07-12T10:00:00Z");

    @Mock TournamentMatchLocalRepository tournamentMatchLocalRepository;
    @Mock Clock clock;
    @InjectMocks TournamentMatchLocalSyncService service;

    private TournamentMatchScheduledDto scheduledDto(String matchId, String eventType) {
        return new TournamentMatchScheduledDto(
                "evt-" + matchId,
                eventType,
                matchId,
                "t-1",
                1,
                1,
                "u1",
                "u2",
                GameType.CHESS,
                "game-1",
                "SCHEDULED",
                SCHEDULED_AT,
                "building-1");
    }

    @Test
    void applyEvents_upsertsSingleScheduledEvent() {
        TournamentMatchScheduledDto dto = scheduledDto("m-1", "TOURNAMENT_MATCH_SCHEDULED");

        service.applyEvents(List.of(dto));

        ArgumentCaptor<TournamentMatchLocal> captor = ArgumentCaptor.forClass(TournamentMatchLocal.class);
        verify(tournamentMatchLocalRepository).save(captor.capture());
        TournamentMatchLocal saved = captor.getValue();
        assertEquals(new TournamentMatchId("m-1"), saved.getId());
        assertEquals(1, saved.getRound());
        assertEquals(1, saved.getBracketPosition());
        assertEquals("u1", saved.getParticipantA());
        assertEquals("u2", saved.getParticipantB());
        assertEquals(GameType.CHESS, saved.getGameType());
        assertEquals("game-1", saved.getGameId());
        assertEquals(TournamentMatchStatus.SCHEDULED, saved.getStatus());
        assertEquals(SCHEDULED_AT, saved.getScheduledAt());
    }

    @Test
    void applyEvents_isIdempotentOnRedelivery() {
        TournamentMatchScheduledDto dto = scheduledDto("m-1", "TOURNAMENT_MATCH_SCHEDULED");
        when(tournamentMatchLocalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.applyEvents(List.of(dto));
        service.applyEvents(List.of(dto));

        ArgumentCaptor<TournamentMatchLocal> captor = ArgumentCaptor.forClass(TournamentMatchLocal.class);
        verify(tournamentMatchLocalRepository, times(2)).save(captor.capture());
        List<TournamentMatchLocal> saved = captor.getAllValues();
        assertEquals(2, saved.size());
        // Upsert-by-PK: both domain objects are equal (identity by matchId).
        assertEquals(saved.get(0), saved.get(1));
        assertEquals(new TournamentMatchId("m-1"), saved.get(0).getId());
    }

    @Test
    void applyEvents_skipsNullEvents() {
        service.applyEvents(Arrays.asList((TournamentMatchScheduledDto) null));

        verify(tournamentMatchLocalRepository, never()).save(any());
    }

    @Test
    void applyEvents_handlesEmptyList() {
        service.applyEvents(List.of());

        verifyNoInteractions(tournamentMatchLocalRepository);
    }

    @Test
    void applyEvents_nullListIsNoOp() {
        service.applyEvents(null);

        verifyNoInteractions(tournamentMatchLocalRepository);
    }

    @Test
    void applyEvents_unknownEventTypeLogsAndSkips() {
        TournamentMatchScheduledDto dto = scheduledDto("m-1", "FOO");

        service.applyEvents(List.of(dto));

        verify(tournamentMatchLocalRepository, never()).save(any());
    }
}