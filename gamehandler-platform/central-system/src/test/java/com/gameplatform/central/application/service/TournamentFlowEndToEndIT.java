package com.gameplatform.central.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.model.GameDefinition;
import com.gameplatform.central.domain.model.Tournament;
import com.gameplatform.central.domain.model.TournamentMatch;
import com.gameplatform.central.domain.model.User;
import com.gameplatform.central.domain.ports.out.GameDefinitionRepository;
import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.central.domain.ports.out.TournamentMatchRepository;
import com.gameplatform.central.domain.ports.out.TournamentRepository;
import com.gameplatform.central.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentFormat;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import com.gameplatform.shared.domain.model.TournamentMatchStatus;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.OutboxEventDto;
import com.gameplatform.shared.dto.TournamentMatchResultDto;
import com.gameplatform.shared.dto.TournamentStandingDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * FASE 6 end-to-end integration test for the central tournament-completion
 * flow on H2. Exercises the full single-elimination lifecycle: schedule a
 * 4-participant tournament → feed {@code TOURNAMENT_MATCH_COMPLETED} events to
 * {@link SyncEventProcessor#processOne} → assert {@code advanceWinner}
 * populates the round-2 parent and emits a {@code TOURNAMENT_MATCH_SCHEDULED}
 * outbox row → complete the final → assert the {@link Tournament} transitions
 * to {@code COMPLETED} and {@link TournamentStandingsService#getStandings}
 * returns ranks {@code 1..4}.
 *
 * <p>The test self-cleans all central tables in {@link #cleanAndSeed()} (H2 is
 * {@code ddl-auto=create-drop}, no {@code @Transactional} on the test class so
 * the {@code REQUIRES_NEW} processing of each event commits independently).
 * The {@link LocalServerRegistryPort} is mocked so the replication schedulers
 * are a no-op (no real outbound REST), matching the
 * {@code ContractTestBase}/{@code SchemaAlignmentTest} precedent.</p>
 *
 * <p><b>Bracket-tree caveat:</b> FASE 5 {@link TournamentBracketService#schedule}
 * only persists round-1 matches (no round-2+ placeholders). The FASE 6
 * {@link TournamentBracketService#advanceWinner} creates the next-round parent
 * when it is absent (and {@code parentRound <= totalRounds}), so the round-2
 * final is created on the first round-1 completion — no placeholder is
 * pre-created (the domain {@link TournamentMatch} forbids a blank
 * {@code participantA}, so an empty-slot placeholder cannot be expressed).</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class TournamentFlowEndToEndIT {

    private static final BuildingId BUILDING_ID = new BuildingId("building-test");

    @MockBean LocalServerRegistryPort localServerRegistryPort;

    @Autowired TournamentService tournamentService;
    @Autowired TournamentRegistrationService tournamentRegistrationService;
    @Autowired TournamentBracketService tournamentBracketService;
    @Autowired TournamentStandingsService tournamentStandingsService;
    @Autowired SyncEventProcessor syncEventProcessor;
    @Autowired TournamentRepository tournamentRepository;
    @Autowired TournamentMatchRepository tournamentMatchRepository;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired GameDefinitionRepository gameDefinitionRepository;
    @Autowired UserRepository userRepository;
    @Autowired ObjectMapper objectMapperHolder;
    @Autowired Clock clock;
    @Autowired DataSource dataSource;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanAndSeed() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String t : List.of(
                "processed_events", "outbox_events", "aggregated_statistics",
                "player_match_facts", "player_statistics", "replication_progress",
                "tournament_standings", "tournament_matches", "tournament_participants",
                "tournament_teams", "tournament_team_members", "tournament_buildings",
                "tournaments", "users", "game_definitions", "local_servers",
                "local_admin_buildings", "failed_login_attempts")) {
            jdbcTemplate.execute("DELETE FROM " + t);
        }
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        Instant now = Instant.now();
        gameDefinitionRepository.save(new GameDefinition(GameType.CHESS, "Chess", 2, 2,
                false, null, now, now));
        userRepository.save(new User(new UserId("u1"), "user1", "hash", "u1@x.com", List.of("PLAYER"), now));
        userRepository.save(new User(new UserId("u2"), "user2", "hash", "u2@x.com", List.of("PLAYER"), now));
        userRepository.save(new User(new UserId("u3"), "user3", "hash", "u3@x.com", List.of("PLAYER"), now));
        userRepository.save(new User(new UserId("u4"), "user4", "hash", "u4@x.com", List.of("PLAYER"), now));
    }

    private TournamentId setupTournament(String id) {
        Instant now = Instant.now();
        Tournament draft = new Tournament(new TournamentId(id), "Cup " + id, GameType.CHESS, false, 1,
                TournamentFormat.SINGLE_ELIMINATION, TournamentStatus.DRAFT, now, null,
                new UserId("u1"), now);
        tournamentService.create(draft, List.of("b1", "b2"));
        tournamentService.open(new TournamentId(id));
        for (String uid : List.of("u1", "u2", "u3", "u4")) {
            tournamentRegistrationService.register(new TournamentId(id), new UserId(uid), null, null);
        }
        tournamentBracketService.schedule(new TournamentId(id));
        return new TournamentId(id);
    }

    private void completeMatch(TournamentMatchId matchId, String winner, String status) throws Exception {
        String payload = objectMapperHolder.writeValueAsString(
                new TournamentMatchResultDto(matchId.value(), winner, null, status));
        OutboxEventDto event = new OutboxEventDto(UUID.randomUUID().toString(),
                "TOURNAMENT_MATCH_COMPLETED", payload, clock.instant());
        syncEventProcessor.processOne(BUILDING_ID, event);
    }

    private List<TournamentMatch> round1Scheduled(TournamentId tId) {
        return tournamentMatchRepository.findByTournament(tId).stream()
                .filter(m -> m.getRound() == 1 && m.getStatus() == TournamentMatchStatus.SCHEDULED)
                .sorted(Comparator.comparingInt(TournamentMatch::getBracketPosition))
                .toList();
    }

    private void createRound2Placeholder(TournamentId tId) {
        // No-op: FASE 6 advanceWinner creates the next-round parent when absent
        // (parentRound <= totalRounds), so the round-2 final is created on the
        // first round-1 completion. Kept as a marker helper to document the
        // resolved contract divergence (the domain TournamentMatch forbids a
        // blank participantA, so an empty-slot placeholder cannot be expressed).
    }

    private List<Integer> assignedRanks(TournamentId tId) {
        return tournamentStandingsService.getStandings(tId).stream()
                .map(TournamentStandingDto::rank)
                .sorted()
                .toList();
    }

    @Test
    void fullTournamentFlow_completesAndAssignsRanks() throws Exception {
        TournamentId tId = setupTournament("t-full");
        List<TournamentMatch> round1 = round1Scheduled(tId);
        assertThat(round1).hasSize(2);
        TournamentMatch m1 = round1.get(0); // bracketPosition 1 (odd → parent participantA)
        TournamentMatch m2 = round1.get(1); // bracketPosition 2 (even → parent participantB)

        String winner1 = m1.getParticipantA();
        String winner2 = m2.getParticipantA();

        completeMatch(m1.getMatchId(), winner1, "COMPLETED");
        completeMatch(m2.getMatchId(), winner2, "COMPLETED");

        // round-2 parent fully populated + a fresh TOURNAMENT_MATCH_SCHEDULED outbox row.
        TournamentMatch round2 = tournamentMatchRepository
                .findByTournamentIdAndRoundAndBracketPositionForUpdate(tId, 2, 1).orElseThrow();
        assertThat(round2.getParticipantA()).isEqualTo(winner1);
        assertThat(round2.getParticipantB()).isEqualTo(winner2);
        assertThat(round2.getStatus()).isEqualTo(TournamentMatchStatus.SCHEDULED);

        Integer scheduledOutbox = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE event_type='TOURNAMENT_MATCH_SCHEDULED'",
                Integer.class);
        assertThat(scheduledOutbox).isEqualTo(3); // 2 round-1 (schedule) + 1 round-2 (advanceWinner)

        completeMatch(round2.getMatchId(), winner1, "COMPLETED");

        Tournament completed = tournamentRepository.findById(tId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(TournamentStatus.COMPLETED);

        List<TournamentStandingDto> standings = tournamentStandingsService.getStandings(tId);
        assertThat(standings).hasSize(4);
        assertThat(assignedRanks(tId)).containsExactly(1, 2, 3, 4);

        TournamentStandingDto champ = standings.stream()
                .filter(s -> s.participantId().equals(winner1)).findFirst().orElseThrow();
        assertThat(champ.wins()).isEqualTo(2);
        assertThat(champ.points()).isEqualTo(6);
        assertThat(champ.rank()).isEqualTo(1);

        TournamentStandingDto runner = standings.stream()
                .filter(s -> s.participantId().equals(winner2)).findFirst().orElseThrow();
        assertThat(runner.wins()).isEqualTo(1);
        assertThat(runner.points()).isEqualTo(3);
        assertThat(runner.rank()).isEqualTo(2);
    }

    @Test
    void abandonedMatch_walkoverAdvanceKeepsTournamentFlowing() throws Exception {
        TournamentId tId = setupTournament("t-abandon");
        List<TournamentMatch> round1 = round1Scheduled(tId);
        assertThat(round1).hasSize(2);
        TournamentMatch m1 = round1.get(0);
        TournamentMatch m2 = round1.get(1);

        String winner1 = m1.getParticipantA();
        // m2's participantA abandons → Q2 walkover winner = the opponent (participantB).
        String walkoverWinner = m2.getParticipantB();

        completeMatch(m1.getMatchId(), winner1, "COMPLETED");
        completeMatch(m2.getMatchId(), walkoverWinner, "ABANDONED");

        // The walkover winner advanced into the round-2 parent (participantB slot).
        TournamentMatch round2 = tournamentMatchRepository
                .findByTournamentIdAndRoundAndBracketPositionForUpdate(tId, 2, 1).orElseThrow();
        assertThat(round2.getParticipantA()).isEqualTo(winner1);
        assertThat(round2.getParticipantB()).isEqualTo(walkoverWinner);
        assertThat(round2.getStatus()).isEqualTo(TournamentMatchStatus.SCHEDULED);

        completeMatch(round2.getMatchId(), winner1, "COMPLETED");

        Tournament completed = tournamentRepository.findById(tId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(TournamentStatus.COMPLETED);

        List<TournamentStandingDto> standings = tournamentStandingsService.getStandings(tId);
        assertThat(standings).hasSize(4);
        assertThat(assignedRanks(tId)).containsExactly(1, 2, 3, 4);

        TournamentStandingDto champ = standings.stream()
                .filter(s -> s.participantId().equals(winner1)).findFirst().orElseThrow();
        assertThat(champ.wins()).isEqualTo(2);
        assertThat(champ.rank()).isEqualTo(1);
    }
}