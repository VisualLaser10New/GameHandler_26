package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gameplatform.local.domain.model.TournamentParticipantLocal;
import com.gameplatform.local.domain.ports.out.AdminRequestRepository;
import com.gameplatform.local.domain.ports.out.TournamentParticipantsLocalRepository;
import com.gameplatform.shared.dto.TournamentParticipantsEventDto;
import com.gameplatform.shared.dto.TournamentParticipantViewDto;
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
 * Pure-Mockito unit tests for {@link TournamentParticipantsLocalSyncService}
 * (PIANO §7.B): full-snapshot replace (delete + insert) per tournamentId;
 * {@code markCompleted} when {@code originatingRequestId != null}.
 */
@ExtendWith(MockitoExtension.class)
class TournamentParticipantsLocalSyncServiceTest {

    private static final Instant UPDATED_AT = Instant.parse("2026-07-12T10:00:00Z");

    @Mock TournamentParticipantsLocalRepository tournamentParticipantsLocalRepository;
    @Mock AdminRequestRepository adminRequestRepository;

    private final Clock clock = Clock.fixed(UPDATED_AT, ZoneId.of("UTC"));
    private TournamentParticipantsLocalSyncService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new TournamentParticipantsLocalSyncService(
                tournamentParticipantsLocalRepository, adminRequestRepository, clock);
    }

    private TournamentParticipantsEventDto participantsDto(String tournamentId, String originatingRequestId, int entries) {
        List<TournamentParticipantViewDto> list = new java.util.ArrayList<>();
        for (int i = 0; i < entries; i++) {
            list.add(new TournamentParticipantViewDto("p-" + i, i % 2 == 0, "Player " + i, UPDATED_AT));
        }
        return new TournamentParticipantsEventDto("evt-" + tournamentId, "TOURNAMENT_PARTICIPANTS_UPSERTED",
                tournamentId, list, originatingRequestId, UPDATED_AT);
    }

    @Test
    void applyEvents_replacesSnapshotForTournament_deleteThenInsert() {
        TournamentParticipantsEventDto dto = participantsDto("t-1", null, 2);

        service.applyEvents(List.of(dto));

        verify(tournamentParticipantsLocalRepository).deleteByTournament(argThat(t -> t.value().equals("t-1")));
        ArgumentCaptor<TournamentParticipantLocal> captor = ArgumentCaptor.forClass(TournamentParticipantLocal.class);
        verify(tournamentParticipantsLocalRepository, times(2)).save(captor.capture());
        List<TournamentParticipantLocal> saved = captor.getAllValues();
        assertEquals(2, saved.size());
        assertEquals("p-0", saved.get(0).getParticipantId());
        assertTrue(saved.get(0).isTeam());   // i=0 → i%2==0 → isTeam=true
        assertFalse(saved.get(1).isTeam());    // i=1 → i%2==1 → isTeam=false
    }

    @Test
    void applyEvents_idempotentOnRedelivery_sameSnapshotTwice() {
        TournamentParticipantsEventDto dto = participantsDto("t-1", null, 1);

        service.applyEvents(List.of(dto));
        service.applyEvents(List.of(dto));

        verify(tournamentParticipantsLocalRepository, times(2)).deleteByTournament(argThat(t -> t.value().equals("t-1")));
        verify(tournamentParticipantsLocalRepository, times(2)).save(any(TournamentParticipantLocal.class));
    }

    @Test
    void applyEvents_emptyParticipantsListStillCallsDelete() {
        TournamentParticipantsEventDto dto = new TournamentParticipantsEventDto("evt-1", "TOURNAMENT_PARTICIPANTS_UPSERTED",
                "t-1", List.of(), null, UPDATED_AT);

        service.applyEvents(List.of(dto));

        verify(tournamentParticipantsLocalRepository).deleteByTournament(any());
        verify(tournamentParticipantsLocalRepository, never()).save(any());
    }

    @Test
    void applyEvents_nullListIsNoOp() {
        service.applyEvents(null);
        verifyNoInteractions(tournamentParticipantsLocalRepository);
        verifyNoInteractions(adminRequestRepository);
    }

    @Test
    void applyEvents_skipsNullEvents() {
        service.applyEvents(Arrays.asList((TournamentParticipantsEventDto) null));
        verifyNoInteractions(tournamentParticipantsLocalRepository);
        verifyNoInteractions(adminRequestRepository);
    }

    @Test
    void applyEvents_unknownEventTypeSkipped() {
        TournamentParticipantsEventDto dto = new TournamentParticipantsEventDto("evt-1", "FOO",
                "t-1", List.of(), null, UPDATED_AT);

        service.applyEvents(List.of(dto));

        verifyNoInteractions(tournamentParticipantsLocalRepository);
        verifyNoInteractions(adminRequestRepository);
    }

    @Test
    void applyEvents_blankTournamentIdSkipped() {
        TournamentParticipantsEventDto dto = new TournamentParticipantsEventDto("evt-1", "TOURNAMENT_PARTICIPANTS_UPSERTED",
                " ", List.of(), null, UPDATED_AT);

        service.applyEvents(List.of(dto));

        verifyNoInteractions(tournamentParticipantsLocalRepository);
        verifyNoInteractions(adminRequestRepository);
    }

    @Test
    void applyEvents_markCompletedWhenOriginatingRequestIdNotNull() {
        TournamentParticipantsEventDto dto = participantsDto("t-1", "req-1", 1);

        service.applyEvents(List.of(dto));

        verify(adminRequestRepository).markCompleted(eq("req-1"), argThat(s -> s.contains("participants")), any());
    }

    @Test
    void applyEvents_originatingRequestIdNull_skipsMarkCompleted() {
        TournamentParticipantsEventDto dto = participantsDto("t-1", null, 1);

        service.applyEvents(List.of(dto));

        verifyNoInteractions(adminRequestRepository);
    }
}
