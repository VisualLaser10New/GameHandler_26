package com.gameplatform.central.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gameplatform.central.domain.model.GameDefinition;
import com.gameplatform.central.domain.model.Tournament;
import com.gameplatform.central.domain.model.TournamentMatch;
import com.gameplatform.central.domain.model.User;
import com.gameplatform.central.domain.ports.out.GameDefinitionRepository;
import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import com.gameplatform.central.domain.ports.out.PlayerStatisticsRepository;
import com.gameplatform.central.domain.ports.out.TournamentMatchRepository;
import com.gameplatform.central.domain.ports.out.TournamentRepository;
import com.gameplatform.central.domain.ports.out.UserRepository;
import com.gameplatform.central.domain.model.PlayerStatistics;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentFormat;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import com.gameplatform.shared.domain.model.TournamentMatchStatus;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.OutboxEventDto;
import com.gameplatform.shared.dto.PlayerStatisticsDto;
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
import java.util.List;
import java.util.UUID;

/**
 * Focus-A full tournament flow with integrated player-statistics projection
 * (PIANO §2.2). Mirrors {@link TournamentFlowEndToEndIT} (4-player, single-
 * elimination completion + standings ranks 1..4) and additionally emits, for each
 * completed tournament match, a synthetic {@code GAME_SESSION_COMPLETED} sync
 * event carrying the enriched participants + winnerId + winCondition fields. The
 * central {@link SyncEventProcessor} projects each event through
 * {@link PlayerStatisticsProjectionService} and {@link StatisticsRepository},
 * so after the final is played:
 *
 * <ul>
 *   <li>{@code aggregated_statistics} holds {@code total_sessions = 3} for the
 *       CHESS bucket on the building/period;</li>
 *   <li>{@code player_statistics} holds the champion {@code matchesPlayed=2,
 *       matchesWon=2}, the runner-up {@code matchesPlayed=2, matchesWon=1} and
 *       the two semi-final losers {@code matchesPlayed=1, matchesWon=0};</li>
 *   <li>{@code player_match_facts} has one row per (session, participant).</li>
 * </ul>
 *
 * This is the cross-cutting GUI flow the JavaFX TournamentsView + MyStatistics
 * view exercise end-to-end (play a scheduled match → advanceWinner → final →
 * recompute standings → query personal statistics), driven here at the
 * use-case / processor level so the test runs on H2 with no MySQL and no MQTT
 * broker, matching {@code TournamentFlowEndToEndIT}'s profile.
 */
@SpringBootTest
@ActiveProfiles("test")
class TournamentFlowWithPlayerStatisticsIT {

    private static final BuildingId BUILDING_ID = new BuildingId("building-test");

    @MockBean LocalServerRegistryPort localServerRegistryPort;

    @Autowired TournamentService tournamentService;
    @Autowired TournamentRegistrationService tournamentRegistrationService;
    @Autowired TournamentBracketService tournamentBracketService;
    @Autowired TournamentStandingsService tournamentStandingsService;
    @Autowired PlayerStatisticsService playerStatisticsService;
    @Autowired PlayerStatisticsRepository playerStatisticsRepository;
    @Autowired SyncEventProcessor syncEventProcessor;
    @Autowired TournamentRepository tournamentRepository;
    @Autowired TournamentMatchRepository tournamentMatchRepository;
    @Autowired GameDefinitionRepository gameDefinitionRepository;
    @Autowired UserRepository userRepository;
    @Autowired Clock clock;
    @Autowired DataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper objectMapper;

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
        for (String uid : List.of("p1", "p2", "p3", "p4")) {
            userRepository.save(new User(new UserId(uid), uid, "hash", uid + "@x.com",
                    List.of("PLAYER"), now));
        }
    }

    /** Drives the schedule + register 4 participants lifecycle of a fresh 4-CHESS tournament. */
    private TournamentId setupTournament(String id) {
        Instant now = Instant.now();
        Tournament draft = new Tournament(new TournamentId(id), "Cup " + id, GameType.CHESS, false, 1,
                TournamentFormat.SINGLE_ELIMINATION, TournamentStatus.DRAFT, now, null,
                new UserId("p1"), now);
        tournamentService.create(draft, List.of("b1", "b2"));
        tournamentService.open(new TournamentId(id));
        for (String uid : List.of("p1", "p2", "p3", "p4")) {
            tournamentRegistrationService.register(new TournamentId(id), new UserId(uid), null, null);
        }
        tournamentBracketService.schedule(new TournamentId(id));
        return new TournamentId(id);
    }

    /** Marks the match COMPLETED/ABANDONED on central via a TOURNAMENT_MATCH_COMPLETED sync event. */
    private void completeMatch(TournamentMatchId matchId, String winner, String status) throws Exception {
        String payload = objectMapper.writeValueAsString(
                new TournamentMatchResultDto(matchId.value(), winner, null, status));
        OutboxEventDto event = new OutboxEventDto(UUID.randomUUID().toString(),
                "TOURNAMENT_MATCH_COMPLETED", payload, clock.instant());
        syncEventProcessor.processOne(BUILDING_ID, event);
    }

    /**
     * Emits a synthetic GAME_SESSION_COMPLETED sync event (the same outbox row
     * the Local {@code GameSessionService.end} writes when a tournament session
     * ends naturally) so the central {@link SyncEventProcessor} projects the
     * participants + winner into {@code player_match_facts} / {@code
     * player_statistics} and updates {@code aggregated_statistics}.
     */
    private void projectSessionCompleted(String winner, List<String> participants) throws Exception {
        String sessionId = UUID.randomUUID().toString();
        String payload = objectMapper.writeValueAsString(java.util.Map.of(
                "eventId", UUID.randomUUID().toString(),
                "occurredAt", clock.instant().toString(),
                "sessionId", sessionId,
                "gameType", "CHESS",
                "durationSeconds", 60,
                "status", "COMPLETED",
                "participants", participants,
                "winnerId", winner,
                "winCondition", "WIN"));
        OutboxEventDto event = new OutboxEventDto(UUID.randomUUID().toString(),
                "GAME_SESSION_COMPLETED", payload, clock.instant());
        syncEventProcessor.processOne(BUILDING_ID, event);
    }

    /** Completes a tournament match AND projects the matching synthetic session-completed event. */
    private void completeMatchWithSession(TournamentMatch m, String winner) throws Exception {
        completeMatch(m.getMatchId(), winner, "COMPLETED");
        projectSessionCompleted(winner, List.of(m.getParticipantA(), m.getParticipantB()));
    }

    private List<TournamentMatch> round1Scheduled(TournamentId tId) {
        return tournamentMatchRepository.findByTournament(tId).stream()
                .filter(m -> m.getRound() == 1 && m.getStatus() == TournamentMatchStatus.SCHEDULED)
                .sorted((a, b) -> Integer.compare(a.getBracketPosition(), b.getBracketPosition()))
                .toList();
    }

    private PlayerStatisticsDto statsFor(String uid, GameType gt) {
        return playerStatisticsService.getStatistics(new UserId(uid), gt).stream()
                .findFirst().orElse(null);
    }

    @Test
    void fullTournamentFlow_completesWithStandingsAndIntegratedPlayerStatistics() throws Exception {
        TournamentId tId = setupTournament("t-stats");
        List<TournamentMatch> round1 = round1Scheduled(tId);
        assertThat(round1).hasSize(2);
        TournamentMatch m1 = round1.get(0); // bracketPosition 1 (p1 vs p2, parent participantA)
        TournamentMatch m2 = round1.get(1); // bracketPosition 2 (p3 vs p4, parent participantB)

        // Semi-final 1 — p1 wins.
        String champion = m1.getParticipantA();
        completeMatchWithSession(m1, champion);

        // Semi-final 2 — p3 (participantA of bracketPosition 2) wins.
        String runnerUp = m2.getParticipantA();
        completeMatchWithSession(m2, runnerUp);

        // Final — round-2 parent populated by advanceWinner; champion wins.
        TournamentMatch finalMatch = tournamentMatchRepository
                .findByTournamentIdAndRoundAndBracketPositionForUpdate(tId, 2, 1).orElseThrow();
        assertThat(finalMatch.getParticipantA()).isEqualTo(champion);
        assertThat(finalMatch.getParticipantB()).isEqualTo(runnerUp);
        completeMatchWithSession(finalMatch, champion);

        // Tournament COMPLETED, standings 1..4.
        Tournament completed = tournamentRepository.findById(tId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(TournamentStatus.COMPLETED);
        List<TournamentStandingDto> standings = tournamentStandingsService.getStandings(tId);
        assertThat(standings).hasSize(4);
        assertThat(standings.stream().map(TournamentStandingDto::rank).sorted().toList())
                .containsExactly(1, 2, 3, 4);
        assertThat(standings.stream().filter(s -> s.participantId().equals(champion))
                .findFirst().orElseThrow().rank()).isEqualTo(1);
        assertThat(standings.stream().filter(s -> s.participantId().equals(runnerUp))
                .findFirst().orElseThrow().rank()).isEqualTo(2);

        // Aggregated statistics — 3 completed CHESS sessions on the building bucket.
        Integer aggSessions = jdbcTemplate.queryForObject(
                "SELECT total_sessions FROM aggregated_statistics WHERE building_id=? AND game_type='CHESS'",
                Integer.class, BUILDING_ID.id());
        assertThat(aggSessions).as("aggregated_statistics.total_sessions covers all 3 matches")
                .isEqualTo(3);

        // Player statistics read-model — projected by PlayerStatisticsProjectionService.
        PlayerStatisticsDto champStats = statsFor(champion, GameType.CHESS);
        assertThat(champStats).as("champion has a player_statistics row").isNotNull();
        assertThat(champStats.matchesPlayed()).isEqualTo(2);
        assertThat(champStats.matchesWon()).isEqualTo(2);

        PlayerStatisticsDto runnerStats = statsFor(runnerUp, GameType.CHESS);
        assertThat(runnerStats).as("runner-up has a player_statistics row").isNotNull();
        assertThat(runnerStats.matchesPlayed()).isEqualTo(2);
        assertThat(runnerStats.matchesWon()).isEqualTo(1);

        // Losers of the two semi-finals — exact loser ids vs champion/runner pairs.
        String los1 = champion.equals(m1.getParticipantA()) ? m1.getParticipantB() : m1.getParticipantA();
        String los2 = runnerUp.equals(m2.getParticipantA()) ? m2.getParticipantB() : m2.getParticipantA();
        for (String loser : List.of(los1, los2)) {
            PlayerStatisticsDto ls = statsFor(loser, GameType.CHESS);
            assertThat(ls).as("loser " + loser + " has a player_statistics row").isNotNull();
            assertThat(ls.matchesPlayed()).isEqualTo(1);
            assertThat(ls.matchesWon()).isEqualTo(0);
        }

        // player_match_facts — one row per (session, participant); 4 matches deep? No, 3 matches
        // x2 participants = 6 facts.
        Integer facts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_match_facts WHERE game_type='CHESS'", Integer.class);
        assertThat(facts).as("player_match_facts has 6 facts (3 sessions x 2 participants)").isEqualTo(6);

        // Direct repository read confirms the projection persisted + read-model remains consistent.
        PlayerStatistics champRow = playerStatisticsRepository
                .findByUserIdAndGameType(new UserId(champion), GameType.CHESS).orElseThrow();
        assertThat(champRow.getMatchesPlayed()).isEqualTo(2);
        assertThat(champRow.getMatchesWon()).isEqualTo(2);
    }
}
