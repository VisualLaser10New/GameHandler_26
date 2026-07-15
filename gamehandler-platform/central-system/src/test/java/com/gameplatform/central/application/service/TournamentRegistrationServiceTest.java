package com.gameplatform.central.application.service;

import com.gameplatform.central.domain.exception.InvalidTournamentException;
import com.gameplatform.central.domain.exception.TournamentRegistrationClosedException;
import com.gameplatform.central.domain.model.Team;
import com.gameplatform.central.domain.model.Tournament;
import com.gameplatform.central.domain.model.TournamentParticipant;
import com.gameplatform.central.domain.model.User;
import com.gameplatform.central.domain.ports.in.EmitTournamentSummaryUseCase;
import com.gameplatform.central.domain.ports.out.TournamentParticipantRepository;
import com.gameplatform.central.domain.ports.out.TournamentRepository;
import com.gameplatform.central.domain.ports.out.TournamentTeamRepository;
import com.gameplatform.central.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentFormat;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.TournamentParticipantDto;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TournamentRegistrationService}, covering the FASE 4
 * individual/team registration, validation invariants (team-size match,
 * captain membership), the registration-closed guard, individual unregister
 * and the participant listing use case. Pure Mockito (no Spring context);
 * uses a real {@link Clock#fixed} so {@code registeredAt}/{@code createdAt}
 * are deterministic.
 */
@ExtendWith(MockitoExtension.class)
class TournamentRegistrationServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-12T10:00:00Z");
    private final Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @Mock
    private TournamentRepository tournamentRepository;
    @Mock
    private TournamentTeamRepository tournamentTeamRepository;
    @Mock
    private TournamentParticipantRepository tournamentParticipantRepository;
    @Mock
    private UserRepository userRepository;

    private TournamentRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new TournamentRegistrationService(
                tournamentRepository, tournamentTeamRepository,
                tournamentParticipantRepository, userRepository, clock);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // register() — individual
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void register_individual_createsParticipant_whenTournamentOpenAndNotTeamBased() {
        TournamentId tid = new TournamentId("t-1");
        UserId captain = new UserId("u-1");
        Tournament open = new Tournament(
                tid, "Test Cup", GameType.CHESS, false, 1,
                TournamentFormat.SINGLE_ELIMINATION, TournamentStatus.OPEN_REGISTRATION,
                FIXED_NOW, null, new UserId("admin"), FIXED_NOW);
        User alice = new User(captain, "alice", "hash", "alice@example.com", List.of("PLAYER"), FIXED_NOW);
        when(tournamentRepository.findById(tid)).thenReturn(Optional.of(open));
        when(userRepository.findById(captain)).thenReturn(Optional.of(alice));
        when(tournamentParticipantRepository.existsByTournamentAndParticipantId(tid, "u-1")).thenReturn(false);
        when(tournamentParticipantRepository.save(any(TournamentParticipant.class))).thenAnswer(inv -> inv.getArgument(0));

        TournamentParticipantDto dto = service.register(tid, captain, null, null);

        assertThat(dto.participantId()).isEqualTo("u-1");
        assertThat(dto.isTeam()).isFalse();
        assertThat(dto.displayName()).isEqualTo("alice");

        verify(tournamentParticipantRepository).save(any(TournamentParticipant.class));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // register() — team
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void register_team_createsTeamAndParticipant_whenSizeMatchesAndCaptainInList() {
        TournamentId tid = new TournamentId("t-1");
        UserId captain = new UserId("captain");
        Tournament open = new Tournament(
                tid, "Team Cup", GameType.FOOSBALL, true, 2,
                TournamentFormat.SINGLE_ELIMINATION, TournamentStatus.OPEN_REGISTRATION,
                FIXED_NOW, null, new UserId("admin"), FIXED_NOW);
        when(tournamentRepository.findById(tid)).thenReturn(Optional.of(open));
        when(tournamentTeamRepository.existsByTournamentAndName(tid, "MyTeam")).thenReturn(false);
        when(tournamentTeamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tournamentParticipantRepository.save(any(TournamentParticipant.class))).thenAnswer(inv -> inv.getArgument(0));

        TournamentParticipantDto dto = service.register(tid, captain, "MyTeam", List.of("captain", "m2"));

        assertThat(dto.isTeam()).isTrue();
        assertThat(dto.displayName()).isEqualTo("MyTeam");

        ArgumentCaptor<Team> teamCaptor = ArgumentCaptor.forClass(Team.class);
        verify(tournamentTeamRepository).save(teamCaptor.capture());
        Team savedTeam = teamCaptor.getValue();
        assertThat(savedTeam.getMembers()).hasSize(2);
        assertThat(savedTeam.getMembers().get(0).value()).isEqualTo("captain");
        assertThat(savedTeam.getMembers().get(1).value()).isEqualTo("m2");
        assertThat(savedTeam.getName()).isEqualTo("MyTeam");

        verify(tournamentParticipantRepository).save(any(TournamentParticipant.class));
    }

    @Test
    void register_team_throwsInvalidTournamentException_whenSizeMismatch() {
        TournamentId tid = new TournamentId("t-1");
        UserId captain = new UserId("captain");
        Tournament open = new Tournament(
                tid, "Team Cup", GameType.FOOSBALL, true, 2,
                TournamentFormat.SINGLE_ELIMINATION, TournamentStatus.OPEN_REGISTRATION,
                FIXED_NOW, null, new UserId("admin"), FIXED_NOW);
        when(tournamentRepository.findById(tid)).thenReturn(Optional.of(open));

        assertThatThrownBy(() -> service.register(tid, captain, "MyTeam", List.of("captain")))
                .isInstanceOf(InvalidTournamentException.class)
                .hasMessageContaining("teamMembers size must equal teamSize 2");
    }

    @Test
    void register_team_throwsInvalidTournamentException_whenCaptainNotInMembers() {
        TournamentId tid = new TournamentId("t-1");
        UserId captain = new UserId("captain");
        Tournament open = new Tournament(
                tid, "Team Cup", GameType.FOOSBALL, true, 2,
                TournamentFormat.SINGLE_ELIMINATION, TournamentStatus.OPEN_REGISTRATION,
                FIXED_NOW, null, new UserId("admin"), FIXED_NOW);
        when(tournamentRepository.findById(tid)).thenReturn(Optional.of(open));

        assertThatThrownBy(() -> service.register(tid, captain, "MyTeam", List.of("m1", "m2")))
                .isInstanceOf(InvalidTournamentException.class)
                .hasMessageContaining("Captain must be a team member");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // register() — BUG-PARTICIPANT-COUNT summary refresh
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void register_individual_emitsSummaryRefreshWithOriginatingRequestId_bugParticipantCount() {
        TournamentId tid = new TournamentId("t-1");
        UserId captain = new UserId("u-1");
        Tournament open = new Tournament(
                tid, "Test Cup", GameType.CHESS, false, 1,
                TournamentFormat.SINGLE_ELIMINATION, TournamentStatus.OPEN_REGISTRATION,
                FIXED_NOW, null, new UserId("admin"), FIXED_NOW);
        User alice = new User(captain, "alice", "hash", "alice@example.com", List.of("PLAYER"), FIXED_NOW);
        when(tournamentRepository.findById(tid)).thenReturn(Optional.of(open));
        when(userRepository.findById(captain)).thenReturn(Optional.of(alice));
        when(tournamentParticipantRepository.existsByTournamentAndParticipantId(tid, "u-1")).thenReturn(false);
        when(tournamentParticipantRepository.save(any(TournamentParticipant.class))).thenAnswer(inv -> inv.getArgument(0));

        EmitTournamentSummaryUseCase emit = mock(EmitTournamentSummaryUseCase.class);
        TournamentRegistrationService full = new TournamentRegistrationService(
                tournamentRepository, tournamentTeamRepository,
                tournamentParticipantRepository, userRepository, clock,
                null, null, emit);

        full.register(tid, captain, null, null, "req-id-1");

        verify(emit).emitSummary(tid, "req-id-1");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // register() — guard
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void register_throwsTournamentRegistrationClosedException_whenStatusNotOpenRegistration() {
        TournamentId tid = new TournamentId("t-1");
        UserId captain = new UserId("u-1");
        Tournament draft = new Tournament(
                tid, "Test Cup", GameType.CHESS, false, 1,
                TournamentFormat.SINGLE_ELIMINATION, TournamentStatus.DRAFT,
                FIXED_NOW, null, new UserId("admin"), FIXED_NOW);
        when(tournamentRepository.findById(tid)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.register(tid, captain, null, null))
                .isInstanceOf(TournamentRegistrationClosedException.class);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // unregister()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void unregister_individual_removesParticipant_whenFound() {
        TournamentId tid = new TournamentId("t-1");
        UserId current = new UserId("u-1");
        Tournament open = new Tournament(
                tid, "Test Cup", GameType.CHESS, false, 1,
                TournamentFormat.SINGLE_ELIMINATION, TournamentStatus.OPEN_REGISTRATION,
                FIXED_NOW, null, new UserId("admin"), FIXED_NOW);
        TournamentParticipant individual = new TournamentParticipant(tid, "u-1", false, "alice", FIXED_NOW);
        when(tournamentRepository.findById(tid)).thenReturn(Optional.of(open));
        when(tournamentParticipantRepository.findByTournamentAndParticipantId(tid, "u-1"))
                .thenReturn(Optional.of(individual));

        service.unregister(tid, current);

        verify(tournamentParticipantRepository).deleteByTournamentAndParticipantId(tid, "u-1");
    }

    @Test
    void unregister_individual_emitsSummaryUpsert_bugParticipantCount() {
        TournamentId tid = new TournamentId("t-1");
        UserId current = new UserId("u-1");
        Tournament open = new Tournament(
                tid, "Test Cup", GameType.CHESS, false, 1,
                TournamentFormat.SINGLE_ELIMINATION, TournamentStatus.OPEN_REGISTRATION,
                FIXED_NOW, null, new UserId("admin"), FIXED_NOW);
        TournamentParticipant individual = new TournamentParticipant(tid, "u-1", false, "alice", FIXED_NOW);
        when(tournamentRepository.findById(tid)).thenReturn(Optional.of(open));
        when(tournamentParticipantRepository.findByTournamentAndParticipantId(tid, "u-1"))
                .thenReturn(Optional.of(individual));

        EmitTournamentSummaryUseCase emit = mock(EmitTournamentSummaryUseCase.class);
        TournamentRegistrationService full = new TournamentRegistrationService(
                tournamentRepository, tournamentTeamRepository,
                tournamentParticipantRepository, userRepository, clock,
                null, null, emit);

        full.unregister(tid, current);

        verify(emit).emitSummary(tid, null);
    }

    @Test
    void unregister_team_emitsSummaryUpsert_bugParticipantCount() {
        TournamentId tid = new TournamentId("t-1");
        UserId current = new UserId("u-1");
        Tournament open = new Tournament(
                tid, "Team Cup", GameType.FOOSBALL, true, 2,
                TournamentFormat.SINGLE_ELIMINATION, TournamentStatus.OPEN_REGISTRATION,
                FIXED_NOW, null, new UserId("admin"), FIXED_NOW);
        com.gameplatform.central.domain.model.Team team = new com.gameplatform.central.domain.model.Team(
                new com.gameplatform.shared.domain.model.TeamId("team-1"), tid, "MyTeam",
                List.of(new UserId("u-1"), new UserId("u-2")), FIXED_NOW);
        when(tournamentRepository.findById(tid)).thenReturn(Optional.of(open));
        when(tournamentParticipantRepository.findByTournamentAndParticipantId(tid, "u-1"))
                .thenReturn(Optional.empty());
        when(tournamentTeamRepository.findByTournamentAndMember(tid, current))
                .thenReturn(Optional.of(team));

        EmitTournamentSummaryUseCase emit = mock(EmitTournamentSummaryUseCase.class);
        TournamentRegistrationService full = new TournamentRegistrationService(
                tournamentRepository, tournamentTeamRepository,
                tournamentParticipantRepository, userRepository, clock,
                null, null, emit);

        full.unregister(tid, current);

        verify(emit).emitSummary(tid, null);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // listParticipants()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void listParticipants_returnsMappedDtos() {
        TournamentId tid = new TournamentId("t-1");
        TournamentParticipant p1 = new TournamentParticipant(tid, "u-1", false, "alice", FIXED_NOW);
        TournamentParticipant p2 = new TournamentParticipant(tid, "team-1", true, "MyTeam", FIXED_NOW);
        when(tournamentParticipantRepository.findByTournament(tid)).thenReturn(List.of(p1, p2));

        List<TournamentParticipantDto> result = service.listParticipants(tid);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).participantId()).isEqualTo("u-1");
        assertThat(result.get(0).isTeam()).isFalse();
        assertThat(result.get(0).displayName()).isEqualTo("alice");
        assertThat(result.get(1).participantId()).isEqualTo("team-1");
        assertThat(result.get(1).isTeam()).isTrue();
        assertThat(result.get(1).displayName()).isEqualTo("MyTeam");
    }
}