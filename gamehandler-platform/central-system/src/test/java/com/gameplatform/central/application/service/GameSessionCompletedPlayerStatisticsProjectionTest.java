package com.gameplatform.central.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gameplatform.central.domain.model.GameDefinition;
import com.gameplatform.central.domain.model.PlayerStatistics;
import com.gameplatform.central.domain.model.User;
import com.gameplatform.central.domain.ports.out.GameDefinitionRepository;
import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import com.gameplatform.central.domain.ports.out.PlayerStatisticsRepository;
import com.gameplatform.central.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;
import com.gameplatform.shared.dto.OutboxEventDto;
import com.gameplatform.shared.dto.PlayerStatisticsDto;
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
 * Central projection coverage for a non-tournament
 * {@code GAME_SESSION_COMPLETED} event (the shape the Local
 * {@code GameSessionService.end} writes for a walk-in / single-player session
 * with no tournament binding). Exercises the {@link SyncEventProcessor} ->
 * {@link PlayerStatisticsProjectionService} path with:
 *
 * <ul>
 *   <li>a single-player {@code SLOT_MACHINE} session whose sole participant
 *       is identified by its user <em>id</em> (UUID) &mdash; the contract the
 *       Game Client Emulator must honour so that
 *       {@code /api/players/me/statistics} (which resolves the authenticated
 *       user id from the JWT) returns the row;</li>
 *   <li>a two-player {@code FOOSBALL} session, asserting both participants are
 *       projected (the loser gets {@code matchesPlayed=1, matchesWon=0});</li>
 *   <li>idempotency &mdash; re-emitting the same {@code (sessionId,participants)}
 *       under a new event id does not double-count (the
 *       {@code saveIfAbsent} / composite-PK guard on {@code player_match_facts}
 *       prevents a second increment).</li>
 * </ul>
 *
 * <p>Runs on H2 with no MySQL and no MQTT broker, mirroring
 * {@code TournamentFlowWithPlayerStatisticsIT}'s profile.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class GameSessionCompletedPlayerStatisticsProjectionTest {

    private static final BuildingId BUILDING_ID = new BuildingId("building-slot-test");

    @MockBean LocalServerRegistryPort localServerRegistryPort;

    @Autowired SyncEventProcessor syncEventProcessor;
    @Autowired PlayerStatisticsService playerStatisticsService;
    @Autowired PlayerStatisticsRepository playerStatisticsRepository;
    @Autowired GameDefinitionRepository gameDefinitionRepository;
    @Autowired UserRepository userRepository;
    @Autowired Clock clock;
    @Autowired DataSource dataSource;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanAndSeed() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String t : List.of(
                "processed_events", "outbox_events", "aggregated_statistics",
                "player_match_facts", "player_statistics", "replication_progress",
                "users", "game_definitions", "local_servers",
                "local_admin_buildings", "failed_login_attempts")) {
            jdbcTemplate.execute("DELETE FROM " + t);
        }
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        Instant now = Instant.now();
        gameDefinitionRepository.save(new GameDefinition(GameType.SLOT_MACHINE, "Slot Machine", 1, 1,
                false, null, now, now));
        gameDefinitionRepository.save(new GameDefinition(GameType.FOOSBALL, "Foosball", 2, 4,
                true, null, now, now));
        userRepository.save(new User(new UserId("slot-player"), "slot-player", "hash",
                "slot@x.com", List.of("PLAYER"), now));
    }

    private void projectCompleted(String gameType, String sessionId, String winnerId,
                                   WinCondition winCondition, List<String> participants) throws Exception {
        String payload = objectMapper.writeValueAsString(java.util.Map.of(
                "eventId", UUID.randomUUID().toString(),
                "occurredAt", clock.instant().toString(),
                "sessionId", sessionId,
                "gameType", gameType,
                "durationSeconds", 42,
                "status", "COMPLETED",
                "participants", participants,
                "winnerId", winnerId,
                "winCondition", winCondition.name()));
        OutboxEventDto event = new OutboxEventDto(UUID.randomUUID().toString(),
                "GAME_SESSION_COMPLETED", payload, clock.instant());
        syncEventProcessor.processOne(BUILDING_ID, event);
    }

    private PlayerStatisticsDto statsFor(String uid, GameType gt) {
        return playerStatisticsService.getStatistics(new UserId(uid), gt).stream()
                .findFirst().orElse(null);
    }

    @Test
    void slotSession_singlePlayerUuidParticipant_projectsUuidKeyedPlayerStatisticsAndFacts() throws Exception {
        String userId = "20a3ff8e-e85a-44aa-ab48-03f6d8ff34e6";
        String sessionId = UUID.randomUUID().toString();

        projectCompleted("SLOT_MACHINE", sessionId, userId, WinCondition.WIN, List.of(userId));

        PlayerStatisticsDto stats = statsFor(userId, GameType.SLOT_MACHINE);
        assertThat(stats).as("SLOT_MACHINE player_statistics row keyed by user id").isNotNull();
        assertThat(stats.userId()).isEqualTo(userId);
        assertThat(stats.gameType()).isEqualTo(GameType.SLOT_MACHINE);
        assertThat(stats.matchesPlayed()).isEqualTo(1);
        assertThat(stats.matchesWon()).isEqualTo(1);

        Integer facts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_match_facts WHERE session_id=? AND user_id=?",
                Integer.class, sessionId, userId);
        assertThat(facts).isEqualTo(1);

        Integer aggSessions = jdbcTemplate.queryForObject(
                "SELECT total_sessions FROM aggregated_statistics WHERE building_id=? AND game_type='SLOT_MACHINE'",
                Integer.class, BUILDING_ID.id());
        assertThat(aggSessions).isEqualTo(1);
    }

    @Test
    void foosballSession_twoPlayers_projectsBothParticipantsWinnerCreditedLoserEmpty() throws Exception {
        String winner = "p-foos-w";
        String loser = "p-foos-l";
        String sessionId = UUID.randomUUID().toString();

        projectCompleted("FOOSBALL", sessionId, winner, WinCondition.WIN, List.of(winner, loser));

        PlayerStatisticsDto wStats = statsFor(winner, GameType.FOOSBALL);
        assertThat(wStats).isNotNull();
        assertThat(wStats.matchesPlayed()).isEqualTo(1);
        assertThat(wStats.matchesWon()).isEqualTo(1);

        PlayerStatisticsDto lStats = statsFor(loser, GameType.FOOSBALL);
        assertThat(lStats).isNotNull();
        assertThat(lStats.matchesPlayed()).isEqualTo(1);
        assertThat(lStats.matchesWon()).isEqualTo(0);

        Integer facts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_match_facts WHERE session_id=?", Integer.class, sessionId);
        assertThat(facts).as("one player_match_facts row per participant").isEqualTo(2);
    }

    @Test
    void reProcessingSameSessionUnderNewEventId_doesNotDoubleCount() throws Exception {
        String userId = "slot-player";
        String sessionId = UUID.randomUUID().toString();

        projectCompleted("SLOT_MACHINE", sessionId, userId, WinCondition.WIN, List.of(userId));
        projectCompleted("SLOT_MACHINE", sessionId, userId, WinCondition.WIN, List.of(userId));

        PlayerStatistics champRow = playerStatisticsRepository
                .findByUserIdAndGameType(new UserId(userId), GameType.SLOT_MACHINE).orElseThrow();
        assertThat(champRow.getMatchesPlayed())
                .as("idempotent projection: same session replayed under a new event id must not increment")
                .isEqualTo(1);
        assertThat(champRow.getMatchesWon()).isEqualTo(1);
    }
}