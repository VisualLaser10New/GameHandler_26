package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gameplatform.local.domain.model.TournamentStandingLocal;
import com.gameplatform.local.domain.ports.out.AdminRequestRepository;
import com.gameplatform.local.domain.ports.out.TournamentStandingsLocalRepository;
import com.gameplatform.shared.dto.TournamentStandingsEventDto;
import com.gameplatform.shared.dto.TournamentStandingDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

/**
 * Pure-Mockito unit tests for {@link TournamentStandingsLocalSyncService}
 * (PIANO §7.B): full-snapshot replace (delete + insert) per tournamentId;
 * {@code markCompleted} when {@code originatingRequestId != null}.
 */
@ExtendWith(MockitoExtension.class)
class TournamentStandingsLocalSyncServiceTest {

    private static final Instant UPDATED_AT = Instant.parse("2026-07-12T10:00:00Z");

    @Mock TournamentStandingsLocalRepository tournamentStandingsLocalRepository;
    @Mock AdminRequestRepository adminRequestRepository;

    private final Clock clock = Clock.fixed(UPDATED_AT, ZoneId.of("UTC"));
    private TournamentStandingsLocalSyncService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new TournamentStandingsLocalSyncService(
                tournamentStandingsLocalRepository, adminRequestRepository, clock);
    }

    private TournamentStandingsEventDto standingsDto(String tournamentId, String originatingRequestId, int entries) {
        List<TournamentStandingDto> list = new java.util.ArrayList<>();
        for (int i = 0; i < entries; i++) {
            list.add(new TournamentStandingDto("p-" + i, "Player " + i, i, entries - i, i * 3, i + 1));
        }
        return new TournamentStandingsEventDto("evt-" + tournamentId, "TOURNAMENT_STANDINGS_UPSERTED",
                tournamentId, list, originatingRequestId, UPDATED_AT);
    }

    @Test
    void applyEvents_replacesSnapshotForTournament_deleteThenInsert() {
        TournamentStandingsEventDto dto = standingsDto("t-1", null, 2);

        service.applyEvents(List.of(dto));

        verify(tournamentStandingsLocalRepository).deleteByTournament(argThat(t -> t.value().equals("t-1")));
        ArgumentCaptor<TournamentStandingLocal> captor = ArgumentCaptor.forClass(TournamentStandingLocal.class);
        verify(tournamentStandingsLocalRepository, times(2)).save(captor.capture());
        List<TournamentStandingLocal> saved = captor.getAllValues();
        assertEquals(2, saved.size());
        assertEquals("p-0", saved.get(0).getParticipantId());
        assertEquals(0, saved.get(0).getWins());
        assertEquals("p-1", saved.get(1).getParticipantId());
        assertEquals(1, saved.get(1).getWins());
    }

    @Test
    void applyEvents_idempotentOnRedelivery_sameSnapshotTwice() {
        TournamentStandingsEventDto dto = standingsDto("t-1", null, 1);

        service.applyEvents(List.of(dto));
        service.applyEvents(List.of(dto));

        verify(tournamentStandingsLocalRepository, times(2)).deleteByTournament(argThat(t -> t.value().equals("t-1")));
        verify(tournamentStandingsLocalRepository, times(2)).save(any(TournamentStandingLocal.class));
    }

    @Test
    void applyEvents_emptyEntriesListStillCallsDelete() {
        TournamentStandingsEventDto dto = new TournamentStandingsEventDto("evt-1", "TOURNAMENT_STANDINGS_UPSERTED",
                "t-1", List.of(), null, UPDATED_AT);

        service.applyEvents(List.of(dto));

        verify(tournamentStandingsLocalRepository).deleteByTournament(any());
        verify(tournamentStandingsLocalRepository, never()).save(any());
    }

    @Test
    void applyEvents_nullListIsNoOp() {
        service.applyEvents(null);
        verifyNoInteractions(tournamentStandingsLocalRepository);
        verifyNoInteractions(adminRequestRepository);
    }

    @Test
    void applyEvents_skipsNullEvents() {
        service.applyEvents(Arrays.asList((TournamentStandingsEventDto) null));
        verifyNoInteractions(tournamentStandingsLocalRepository);
        verifyNoInteractions(adminRequestRepository);
    }

    @Test
    void applyEvents_unknownEventTypeSkipped() {
        TournamentStandingsEventDto dto = new TournamentStandingsEventDto("evt-1", "FOO",
                "t-1", List.of(), null, UPDATED_AT);

        service.applyEvents(List.of(dto));

        verifyNoInteractions(tournamentStandingsLocalRepository);
        verifyNoInteractions(adminRequestRepository);
    }

    @Test
    void applyEvents_blankTournamentIdSkipped() {
        TournamentStandingsEventDto dto = new TournamentStandingsEventDto("evt-1", "TOURNAMENT_STANDINGS_UPSERTED",
                " ", List.of(), null, UPDATED_AT);

        service.applyEvents(List.of(dto));

        verifyNoInteractions(tournamentStandingsLocalRepository);
        verifyNoInteractions(adminRequestRepository);
    }

    @Test
    void applyEvents_markCompletedWhenOriginatingRequestIdNotNull() {
        TournamentStandingsEventDto dto = standingsDto("t-1", "req-1", 1);

        service.applyEvents(List.of(dto));

        verify(adminRequestRepository).markCompleted(eq("req-1"), argThat(s -> s.contains("applied")), any());
    }

    @Test
    void applyEvents_originatingRequestIdNull_skipsMarkCompleted() {
        TournamentStandingsEventDto dto = standingsDto("t-1", null, 1);

        service.applyEvents(List.of(dto));

        verifyNoInteractions(adminRequestRepository);
    }
}
