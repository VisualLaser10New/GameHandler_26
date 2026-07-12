package com.gameplatform.central.application.service;

import com.gameplatform.central.domain.exception.InvalidTournamentStateException;
import com.gameplatform.central.domain.model.Tournament;
import com.gameplatform.central.domain.model.TournamentMatch;
import com.gameplatform.central.domain.model.TournamentParticipant;
import com.gameplatform.central.domain.ports.out.TournamentMatchOutboxPort;
import com.gameplatform.central.domain.ports.out.TournamentMatchRepository;
import com.gameplatform.central.domain.ports.out.TournamentParticipantRepository;
import com.gameplatform.central.domain.ports.out.TournamentRepository;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentFormat;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import com.gameplatform.shared.domain.model.TournamentMatchStatus;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.TournamentMatchDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure-Mockito unit tests for {@link TournamentBracketService}, covering FASE 5
 * round-1 single-elimination bracket generation. Verifies the top-seeds-get-byes
 * convention, SCHEDULED/BYE row layout, atomic outbox emission only for
 * SCHEDULED matches, the {@code OPEN_REGISTRATION -> IN_PROGRESS} transition,
 * the SINGLE_ELIMINATION format guard, the {@code >= 2 participants} guard and
 * standings seeding for every participant.
 *
 * <p>{@code tournamentMatchRepository.save(any())} is stubbed with
 * {@code thenAnswer(inv -> inv.getArgument(0))} so the saved {@link TournamentMatch}
 * echoes back the same instance — this lets the tests assert on the generated
 * matches even though each carries a random-UUID {@code matchId}.
 */
@ExtendWith(MockitoExtension.class)
class TournamentBracketServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-12T10:00:00Z");
    private final Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @Mock
    private TournamentRepository tournamentRepository;
    @Mock
    private TournamentParticipantRepository tournamentParticipantRepository;
    @Mock
    private TournamentMatchRepository tournamentMatchRepository;
    @Mock
    private TournamentMatchOutboxPort tournamentMatchOutboxPort;
    @Mock
    private TournamentStandingsService tournamentStandingsService;

    private TournamentBracketService service;

    @BeforeEach
    void setUp() {
        service = new TournamentBracketService(tournamentRepository,
                tournamentParticipantRepository, tournamentMatchRepository,
                tournamentMatchOutboxPort, tournamentStandingsService, clock);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private Tournament openRegistrationTournament(String id) {
        return new Tournament(
                new TournamentId(id), "Test Cup", GameType.CHESS, false, 1,
                TournamentFormat.SINGLE_ELIMINATION, TournamentStatus.OPEN_REGISTRATION,
                FIXED_NOW, null, new UserId("admin-1"), FIXED_NOW);
    }

    private Tournament inProgressTournament(String id) {
        return new Tournament(
                new TournamentId(id), "Test Cup", GameType.CHESS, false, 1,
                TournamentFormat.SINGLE_ELIMINATION, TournamentStatus.IN_PROGRESS,
                FIXED_NOW, null, new UserId("admin-1"), FIXED_NOW);
    }

    private Tournament roundRobinTournament(String id) {
        return new Tournament(
                new TournamentId(id), "Test Cup", GameType.CHESS, false, 1,
                TournamentFormat.ROUND_ROBIN, TournamentStatus.OPEN_REGISTRATION,
                FIXED_NOW, null, new UserId("admin-1"), FIXED_NOW);
    }

    private TournamentParticipant participant(String id, int offsetSeconds) {
        return new TournamentParticipant(
                new TournamentId("t-1"), id, false, "User " + id,
                FIXED_NOW.plusSeconds(offsetSeconds));
    }

    /**
     * Returns participants {@code P1..Pn} with strictly increasing
     * {@code registeredAt} offsets (60s apart), so the service's
     * {@code registeredAt ASC} sort keeps them in P1..Pn order.
     */
    private List<TournamentParticipant> participants(int n) {
        List<TournamentParticipant> list = new ArrayList<>();
        for (int k = 1; k <= n; k++) {
            list.add(participant("P" + k, (k - 1) * 60));
        }
        return list;
    }

    /**
     * Returns the same 5 participants as {@link #participants(int)} would for n=5
     * (P1..P5 with 60s-apart registeredAt) but inserted in a shuffled repo-return
     * order. The service sorts by registeredAt ASC so seeding is still P1..P5.
     */
    private List<TournamentParticipant> shuffledParticipants5() {
        return List.of(
                participant("P5", 240),
                participant("P3", 120),
                participant("P1", 0),
                participant("P4", 180),
                participant("P2", 60));
    }

    private void stubHappyPath(Tournament tournament, List<TournamentParticipant> participants) {
        when(tournamentRepository.findById(any())).thenReturn(Optional.of(tournament));
        when(tournamentParticipantRepository.findByTournament(any())).thenReturn(participants);
        when(tournamentMatchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @SuppressWarnings("unchecked")
    private void verifySeed(List<String> expectedIds) {
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(tournamentStandingsService).seedStandings(eq(new TournamentId("t-1")), captor.capture());
        assertThat(captor.getValue()).containsExactlyElementsOf(expectedIds);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // schedule() — bracket shapes
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void schedule_returnsSingleMatchFor2Participants() {
        stubHappyPath(openRegistrationTournament("t-1"), participants(2));

        List<TournamentMatchDto> result = service.schedule(new TournamentId("t-1"));

        assertThat(result).hasSize(1);
        TournamentMatchDto m = result.get(0);
        assertThat(m.status()).isEqualTo(TournamentMatchStatus.SCHEDULED);
        assertThat(m.participantA()).isEqualTo("P1");
        assertThat(m.participantB()).isEqualTo("P2");
        assertThat(m.winner()).isNull();
        assertThat(m.round()).isEqualTo(1);
        assertThat(m.bracketPosition()).isEqualTo(1);

        verify(tournamentMatchRepository, times(1)).save(any());
        verify(tournamentMatchOutboxPort, times(1)).publishScheduled(any(), any());
        verifySeed(List.of("P1", "P2"));
    }

    @Test
    void schedule_returns1Scheduled1ByeFor3Participants() {
        stubHappyPath(openRegistrationTournament("t-1"), participants(3));

        List<TournamentMatchDto> result = service.schedule(new TournamentId("t-1"));

        assertThat(result).hasSize(2);
        List<TournamentMatchDto> byes = result.stream()
                .filter(d -> d.status() == TournamentMatchStatus.BYE).toList();
        List<TournamentMatchDto> scheduled = result.stream()
                .filter(d -> d.status() == TournamentMatchStatus.SCHEDULED).toList();
        assertThat(byes).hasSize(1);
        assertThat(scheduled).hasSize(1);

        TournamentMatchDto bye = byes.get(0);
        assertThat(bye.participantB()).isNull();
        assertThat(bye.winner()).isEqualTo(bye.participantA());
        assertThat(bye.bracketPosition()).isEqualTo(1);

        TournamentMatchDto sche = scheduled.get(0);
        assertThat(sche.participantA()).isEqualTo("P2");
        assertThat(sche.participantB()).isEqualTo("P3");
        assertThat(sche.bracketPosition()).isEqualTo(2);

        verify(tournamentMatchOutboxPort, times(1)).publishScheduled(any(), any());
        verifySeed(List.of("P1", "P2", "P3"));
    }

    @Test
    void schedule_returns2MatchesFor4Participants() {
        stubHappyPath(openRegistrationTournament("t-1"), participants(4));

        List<TournamentMatchDto> result = service.schedule(new TournamentId("t-1"));

        assertThat(result).hasSize(2);
        assertThat(result).filteredOn(d -> d.status() == TournamentMatchStatus.BYE).isEmpty();
        assertThat(result).extracting(TournamentMatchDto::participantA, TournamentMatchDto::participantB)
                .containsExactly(tuple("P1", "P4"), tuple("P2", "P3"));

        verify(tournamentMatchOutboxPort, times(2)).publishScheduled(any(), any());
        verifySeed(List.of("P1", "P2", "P3", "P4"));
    }

    @Test
    void schedule_returns1Scheduled3ByesFor5Participants() {
        stubHappyPath(openRegistrationTournament("t-1"), participants(5));

        List<TournamentMatchDto> result = service.schedule(new TournamentId("t-1"));

        assertThat(result).hasSize(4);
        assertThat(result).filteredOn(d -> d.status() == TournamentMatchStatus.BYE).hasSize(3);
        List<TournamentMatchDto> scheduled = result.stream()
                .filter(d -> d.status() == TournamentMatchStatus.SCHEDULED).toList();
        assertThat(scheduled).hasSize(1);
        assertThat(scheduled.get(0).participantA()).isEqualTo("P4");
        assertThat(scheduled.get(0).participantB()).isEqualTo("P5");
        assertThat(scheduled.get(0).bracketPosition()).isEqualTo(4);

        verify(tournamentMatchOutboxPort, times(1)).publishScheduled(any(), any());
        verifySeed(List.of("P1", "P2", "P3", "P4", "P5"));
    }

    @Test
    void schedule_returns2Scheduled2ByesFor6Participants() {
        stubHappyPath(openRegistrationTournament("t-1"), participants(6));

        List<TournamentMatchDto> result = service.schedule(new TournamentId("t-1"));

        assertThat(result).hasSize(4);
        assertThat(result).filteredOn(d -> d.status() == TournamentMatchStatus.BYE).hasSize(2);
        List<TournamentMatchDto> scheduled = result.stream()
                .filter(d -> d.status() == TournamentMatchStatus.SCHEDULED).toList();
        assertThat(scheduled).extracting(TournamentMatchDto::participantA, TournamentMatchDto::participantB)
                .containsExactly(tuple("P3", "P6"), tuple("P4", "P5"));
        assertThat(scheduled).extracting(TournamentMatchDto::bracketPosition)
                .containsExactly(3, 4);

        verify(tournamentMatchOutboxPort, times(2)).publishScheduled(any(), any());
        verifySeed(List.of("P1", "P2", "P3", "P4", "P5", "P6"));
    }

    @Test
    void schedule_returns3Scheduled1ByeFor7Participants() {
        stubHappyPath(openRegistrationTournament("t-1"), participants(7));

        List<TournamentMatchDto> result = service.schedule(new TournamentId("t-1"));

        assertThat(result).hasSize(4);
        assertThat(result).filteredOn(d -> d.status() == TournamentMatchStatus.BYE).hasSize(1);
        List<TournamentMatchDto> scheduled = result.stream()
                .filter(d -> d.status() == TournamentMatchStatus.SCHEDULED).toList();
        assertThat(scheduled).extracting(TournamentMatchDto::participantA, TournamentMatchDto::participantB)
                .containsExactly(tuple("P2", "P7"), tuple("P3", "P6"), tuple("P4", "P5"));

        verify(tournamentMatchOutboxPort, times(3)).publishScheduled(any(), any());
        verifySeed(List.of("P1", "P2", "P3", "P4", "P5", "P6", "P7"));
    }

    @Test
    void schedule_returns4MatchesFor8Participants() {
        stubHappyPath(openRegistrationTournament("t-1"), participants(8));

        List<TournamentMatchDto> result = service.schedule(new TournamentId("t-1"));

        assertThat(result).hasSize(4);
        assertThat(result).filteredOn(d -> d.status() == TournamentMatchStatus.BYE).isEmpty();
        assertThat(result).extracting(TournamentMatchDto::participantA, TournamentMatchDto::participantB)
                .containsExactly(tuple("P1", "P8"), tuple("P2", "P7"),
                        tuple("P3", "P6"), tuple("P4", "P5"));

        verify(tournamentMatchOutboxPort, times(4)).publishScheduled(any(), any());
        verifySeed(List.of("P1", "P2", "P3", "P4", "P5", "P6", "P7", "P8"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // schedule() — state transition
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void schedule_transitionsTournamentToInProgress() {
        when(tournamentRepository.findById(any())).thenReturn(Optional.of(openRegistrationTournament("t-1")));
        when(tournamentParticipantRepository.findByTournament(any())).thenReturn(participants(2));
        when(tournamentMatchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tournamentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.schedule(new TournamentId("t-1"));

        ArgumentCaptor<Tournament> captor = ArgumentCaptor.forClass(Tournament.class);
        verify(tournamentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TournamentStatus.IN_PROGRESS);
    }

    @Test
    void schedule_throwsWhenNotOpenRegistration() {
        when(tournamentRepository.findById(any())).thenReturn(Optional.of(inProgressTournament("t-1")));

        assertThatThrownBy(() -> service.schedule(new TournamentId("t-1")))
                .isInstanceOf(InvalidTournamentStateException.class);

        verify(tournamentMatchRepository, never()).save(any());
        verify(tournamentMatchOutboxPort, never()).publishScheduled(any(), any());
    }

    @Test
    void schedule_throwsWhenRoundRobinFormat() {
        when(tournamentRepository.findById(any())).thenReturn(Optional.of(roundRobinTournament("t-1")));

        assertThatThrownBy(() -> service.schedule(new TournamentId("t-1")))
                .isInstanceOf(InvalidTournamentStateException.class);

        verify(tournamentMatchRepository, never()).save(any());
        verify(tournamentMatchOutboxPort, never()).publishScheduled(any(), any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // schedule() — standings seed + outbox emission
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void schedule_seedsStandingsForAllParticipants() {
        stubHappyPath(openRegistrationTournament("t-1"), participants(5));

        service.schedule(new TournamentId("t-1"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(tournamentStandingsService).seedStandings(eq(new TournamentId("t-1")), captor.capture());
        assertThat(captor.getValue()).containsExactly("P1", "P2", "P3", "P4", "P5");
    }

    @Test
    void schedule_emitsOutboxOnlyForScheduledMatches() {
        stubHappyPath(openRegistrationTournament("t-1"), participants(5));

        service.schedule(new TournamentId("t-1"));

        ArgumentCaptor<TournamentMatch> matchCaptor = ArgumentCaptor.forClass(TournamentMatch.class);
        verify(tournamentMatchRepository, times(4)).save(matchCaptor.capture());
        verify(tournamentMatchOutboxPort, times(1)).publishScheduled(matchCaptor.capture(), any());

        // 4 matches are persisted (3 BYE + 1 SCHEDULED) but only 1 outbox event is emitted,
        // and the emitted match MUST be the SCHEDULED one (BYE rows never reach the outbox).
        TournamentMatch emitted = matchCaptor.getValue();
        assertThat(emitted.getStatus()).isEqualTo(TournamentMatchStatus.SCHEDULED);
    }

    @Test
    void schedule_isTopSeedsGetByesConvention() {
        when(tournamentRepository.findById(any())).thenReturn(Optional.of(openRegistrationTournament("t-1")));
        when(tournamentParticipantRepository.findByTournament(any())).thenReturn(shuffledParticipants5());
        when(tournamentMatchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.schedule(new TournamentId("t-1"));

        ArgumentCaptor<TournamentMatch> matchCaptor = ArgumentCaptor.forClass(TournamentMatch.class);
        verify(tournamentMatchRepository, times(4)).save(matchCaptor.capture());

        List<TournamentMatch> byes = matchCaptor.getAllValues().stream()
                .filter(m -> m.getStatus() == TournamentMatchStatus.BYE).toList();
        assertThat(byes).extracting(TournamentMatch::getParticipantA)
                .containsExactlyInAnyOrder("P1", "P2", "P3");

        List<TournamentMatch> scheduled = matchCaptor.getAllValues().stream()
                .filter(m -> m.getStatus() == TournamentMatchStatus.SCHEDULED).toList();
        assertThat(scheduled).hasSize(1);
        assertThat(scheduled.get(0).getParticipantA()).isEqualTo("P4");
        assertThat(scheduled.get(0).getParticipantB()).isEqualTo("P5");
    }

    @Test
    void schedule_throwsWhenFewerThan2Participants() {
        when(tournamentRepository.findById(any())).thenReturn(Optional.of(openRegistrationTournament("t-1")));
        when(tournamentParticipantRepository.findByTournament(any())).thenReturn(participants(1));

        assertThatThrownBy(() -> service.schedule(new TournamentId("t-1")))
                .isInstanceOf(InvalidTournamentStateException.class);

        verify(tournamentMatchRepository, never()).save(any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // findByTournament()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void findByTournament_returnsMatchesAsDtos() {
        TournamentMatch bye = new TournamentMatch(
                new TournamentMatchId("m-bye"), new TournamentId("t-1"), 1, 1,
                "P1", null, null, null, null, "P1",
                TournamentMatchStatus.BYE, null, null, null);
        TournamentMatch scheduled = new TournamentMatch(
                new TournamentMatchId("m-1"), new TournamentId("t-1"), 1, 2,
                "P2", "P3", "b-1", "g-1", null, null,
                TournamentMatchStatus.SCHEDULED, null, null, null);
        when(tournamentMatchRepository.findByTournament(new TournamentId("t-1")))
                .thenReturn(List.of(bye, scheduled));

        List<TournamentMatchDto> result = service.findByTournament(new TournamentId("t-1"));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo("m-bye");
        assertThat(result.get(0).status()).isEqualTo(TournamentMatchStatus.BYE);
        assertThat(result.get(0).participantA()).isEqualTo("P1");
        assertThat(result.get(0).winner()).isEqualTo("P1");
        assertThat(result.get(1).id()).isEqualTo("m-1");
        assertThat(result.get(1).status()).isEqualTo(TournamentMatchStatus.SCHEDULED);
        assertThat(result.get(1).buildingId()).isEqualTo("b-1");
        assertThat(result.get(1).gameId()).isEqualTo("g-1");
    }
}