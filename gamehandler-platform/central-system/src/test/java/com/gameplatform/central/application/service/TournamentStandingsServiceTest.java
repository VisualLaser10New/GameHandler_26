package com.gameplatform.central.application.service;

import com.gameplatform.central.domain.model.TournamentParticipant;
import com.gameplatform.central.domain.model.TournamentStanding;
import com.gameplatform.central.domain.ports.out.TournamentMatchRepository;
import com.gameplatform.central.domain.ports.out.TournamentParticipantRepository;
import com.gameplatform.central.domain.ports.out.TournamentStandingRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.dto.TournamentStandingDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure-Mockito unit tests for {@link TournamentStandingsService}, covering
 * FASE 5: {@code getStandings} read mapping + sorting (points desc, wins desc,
 * participantId asc, displayName resolved from participants) and the idempotent
 * zero-init {@code seedStandings}.
 *
 * <p>The test class lives in the same package as the service so the
 * package-visible {@code seedStandings} method can be exercised directly
 * without reflection.
 */
@ExtendWith(MockitoExtension.class)
class TournamentStandingsServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-12T10:00:00Z");
    private final Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @Mock
    private TournamentStandingRepository tournamentStandingRepository;
    @Mock
    private TournamentParticipantRepository tournamentParticipantRepository;
    @Mock
    private TournamentMatchRepository tournamentMatchRepository;

    private TournamentStandingsService service;

    @BeforeEach
    void setUp() {
        service = new TournamentStandingsService(
                tournamentStandingRepository, tournamentParticipantRepository,
                tournamentMatchRepository, clock);
    }

    private TournamentParticipant participant(TournamentId tid, String id, String displayName) {
        return new TournamentParticipant(tid, id, false, displayName, FIXED_NOW);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // getStandings()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void getStandings_returnsAllSortedByPointsDescThenWinsDesc() {
        TournamentId tid = new TournamentId("t-1");
        TournamentStanding rowA = new TournamentStanding(tid, "A", 2, 0, 6, null);
        TournamentStanding rowB = new TournamentStanding(tid, "B", 1, 1, 3, null);
        TournamentStanding rowC = new TournamentStanding(tid, "C", 2, 0, 6, null);
        when(tournamentStandingRepository.findByTournament(tid)).thenReturn(List.of(rowB, rowA, rowC));
        when(tournamentParticipantRepository.findByTournament(tid)).thenReturn(List.of(
                participant(tid, "A", "Alice"),
                participant(tid, "B", "Bob"),
                participant(tid, "C", "Carol")));

        List<TournamentStandingDto> result = service.getStandings(tid);

        assertThat(result).extracting(
                        TournamentStandingDto::participantId,
                        TournamentStandingDto::displayName,
                        TournamentStandingDto::wins,
                        TournamentStandingDto::losses,
                        TournamentStandingDto::points,
                        TournamentStandingDto::rank)
                .containsExactly(
                        tuple("A", "Alice", 2, 0, 6, null),
                        tuple("C", "Carol", 2, 0, 6, null),
                        tuple("B", "Bob", 1, 1, 3, null));
    }

    @Test
    void getStandings_resolvesDisplayNameFromParticipants() {
        TournamentId tid = new TournamentId("t-1");
        when(tournamentStandingRepository.findByTournament(tid)).thenReturn(
                List.of(new TournamentStanding(tid, "A", 1, 0, 3, null)));
        when(tournamentParticipantRepository.findByTournament(tid)).thenReturn(
                List.of(participant(tid, "A", "Alice")));

        List<TournamentStandingDto> result = service.getStandings(tid);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).displayName()).isEqualTo("Alice");
    }

    @Test
    void getStandings_returnsEmptyWhenNoStandings() {
        TournamentId tid = new TournamentId("t-1");
        when(tournamentStandingRepository.findByTournament(tid)).thenReturn(List.of());
        when(tournamentParticipantRepository.findByTournament(tid)).thenReturn(List.of());

        List<TournamentStandingDto> result = service.getStandings(tid);

        assertThat(result).isEmpty();
    }

    @Test
    void getStandings_returnsEmptyWhenTournamentIdIsNull() {
        List<TournamentStandingDto> result = service.getStandings(null);

        assertThat(result).isEmpty();
        verify(tournamentStandingRepository, never()).findByTournament(any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // seedStandings()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void seedStandings_writesZeroInitRowPerParticipant() {
        TournamentId tid = new TournamentId("t-1");
        when(tournamentStandingRepository.findByTournamentAndParticipantId(eq(tid), any()))
                .thenReturn(Optional.empty());

        service.seedStandings(tid, List.of("A", "B", "C"));

        ArgumentCaptor<TournamentStanding> captor = ArgumentCaptor.forClass(TournamentStanding.class);
        verify(tournamentStandingRepository, times(3)).save(captor.capture());
        List<TournamentStanding> saved = captor.getAllValues();
        assertThat(saved).extracting(TournamentStanding::getParticipantId)
                .containsExactly("A", "B", "C");
        assertThat(saved).allSatisfy(s -> {
            assertThat(s.getWins()).isZero();
            assertThat(s.getLosses()).isZero();
            assertThat(s.getPoints()).isZero();
            assertThat(s.getRank()).isNull();
        });
    }

    @Test
    void seedStandings_isIdempotentWhenRowAlreadyExists() {
        TournamentId tid = new TournamentId("t-1");
        TournamentStanding existingB = new TournamentStanding(tid, "B", 5, 3, 9, 2);
        when(tournamentStandingRepository.findByTournamentAndParticipantId(eq(tid), eq("B")))
                .thenReturn(Optional.of(existingB));
        when(tournamentStandingRepository.findByTournamentAndParticipantId(eq(tid), eq("A")))
                .thenReturn(Optional.empty());
        when(tournamentStandingRepository.findByTournamentAndParticipantId(eq(tid), eq("C")))
                .thenReturn(Optional.empty());

        service.seedStandings(tid, List.of("A", "B", "C"));

        verify(tournamentStandingRepository, times(2)).save(any());
        verify(tournamentStandingRepository).findByTournamentAndParticipantId(eq(tid), eq("B"));
    }

    @Test
    void seedStandings_isNoOpWhenTournamentIdIsNull() {
        service.seedStandings(null, List.of("A", "B"));

        verify(tournamentStandingRepository, never()).save(any());
    }

    @Test
    void seedStandings_isNoOpWhenParticipantIdsIsNull() {
        service.seedStandings(new TournamentId("t-1"), null);

        verify(tournamentStandingRepository, never()).save(any());
    }
}