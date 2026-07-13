package com.gameplatform.central.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gameplatform.central.domain.model.GameDefinition;
import com.gameplatform.central.domain.model.OutboxEvent;
import com.gameplatform.central.domain.model.Tournament;
import com.gameplatform.central.domain.model.User;
import com.gameplatform.central.domain.ports.in.AssignLocalAdminBuildingsUseCase;
import com.gameplatform.central.domain.ports.out.GameDefinitionRepository;
import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.central.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentFormat;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.OutboxEventDto;
import com.gameplatform.shared.dto.RoleAssignmentRequestedEventDto;
import com.gameplatform.shared.dto.StatisticsDto;
import com.gameplatform.shared.dto.TournamentDto;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Focus-D PLATFORM_ADMIN differential E2E flows on Central H2. Fills the
 * {@code PlatformAdminView} GUI branches not already covered by
 * {@code TournamentFlowEndToEndIT} / {@code TournamentFlowWithPlayerStatisticsIT}
 * (the tournament COMPLETED path) or by the standalone Mockito unit tests
 * ({@code TournamentServiceTest}, {@code LocalAdminBuildingServiceTest}). Each
 * sub-flow runs through the real use-case / processor / aggregation service,
 * asserting the underlying H2 rows + the outbox emission, so the layer above
 * the controller (which is covered by the slice tests) is exercised
 * end-to-end at the service level as the spec requires:
 *
 * <ul>
 *   <li>role-assignment central side: feeds a {@code ROLE_ASSIGNMENT_REQUESTED}
 *       sync event through {@link SyncEventProcessor#processOne}; the central
 *       {@code UserService.updateUser} mutates the target {@code users.roles}
 *       column and emits a {@code USER_UPDATED} outbox row for the Local
 *       {@code admin_requests_local} carry-back;</li>
 *   <li>LOCAL_ADMIN↔building binding via {@link AssignLocalAdminBuildingsUseCase}:
 *       a {@code local_admin_buildings} row is persisted AND a
 *       {@code LOCAL_ADMIN_BUILDING_ASSIGNED} outbox row is emitted (the
 *       Mockito {@code LocalAdminBuildingServiceTest} only verifies the
 *       {@code save} mock invocation; this asserts the actual DB row +
 *       outbox payload);</li>
 *   <li>tournament lifecycle DRAFT &rarr; OPEN_REGISTRATION &rarr; CANCELLED
 *       (the cancel-from-OPEN branch <em>not</em> covered by
 *       {@code TournamentServiceTest.cancel_transitionsToCancelled_whenStatusIsDraft});
 *       verifies {@code tournaments.status=CANCELLED} + the matching
 *       {@code TOURNAMENT_SUMMARY_UPSERTED} outbox row carries the CANCELLED
 *       snapshot;</li>
 *   <li>central global statistics query
 *       {@link StatisticsAggregationService#getStatistics} reads the seeded
 *       {@code aggregated_statistics} row (the Mockito slice
 *       {@code StatisticsControllerTest} mocks the use case).</li>
 * </ul>
 *
 * <p>Profile/conventions match {@code TournamentFlowEndToEndIT}: {@code test}
 * profile (H2 in-memory), {@code @MockBean LocalServerRegistryPort} disables
 * the replication schedulers, the class is not {@code @Transactional} so the
 * {@code REQUIRES_NEW} processing of {@code processOne} commits independently.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class PlatformAdminFlowsIT {

    private static final BuildingId BUILDING_ID = new BuildingId("building-test");

    @MockBean LocalServerRegistryPort localServerRegistryPort;

    @Autowired SyncEventProcessor syncEventProcessor;
    @Autowired TournamentService tournamentService;
    @Autowired AssignLocalAdminBuildingsUseCase assignLocalAdminBuildingsUseCase;
    @Autowired StatisticsAggregationService statisticsAggregationService;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired GameDefinitionRepository gameDefinitionRepository;
    @Autowired UserRepository userRepository;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper objectMapper;
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
        userRepository.save(new User(new UserId("target"), "target-user", "hash",
                "target@example.com", List.of("PLAYER"), now));
        userRepository.save(new User(new UserId("admin-la"), "admin-la-user", "hash",
                "adminla@example.com", List.of("LOCAL_ADMIN"), now));
    }

    @Test
    void roleAssignmentRequested_centralProcessor_updatesUserRolesAndEmitsUserUpdatedOutbox() throws Exception {
        String requestId = UUID.randomUUID().toString();
        RoleAssignmentRequestedEventDto dto = new RoleAssignmentRequestedEventDto(
                requestId, "ROLE_ASSIGNMENT_REQUESTED", requestId,
                "platform-admin", "PLATFORM_ADMIN", BUILDING_ID.id(),
                "target", List.of("PLAYER", "LOCAL_ADMIN"), clock.instant());
        OutboxEventDto event = new OutboxEventDto(UUID.randomUUID().toString(),
                "ROLE_ASSIGNMENT_REQUESTED", objectMapper.writeValueAsString(dto), clock.instant());

        boolean processed = syncEventProcessor.processOne(BUILDING_ID, event);
        assertThat(processed).isTrue();

        String roles = jdbcTemplate.queryForObject(
                "SELECT roles FROM users WHERE id=?", String.class, "target");
        assertThat(roles).contains("PLAYER", "LOCAL_ADMIN");

        Integer userUpdatedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE event_type='USER_UPDATED'", Integer.class);
        assertThat(userUpdatedCount).isEqualTo(1);
    }

    @Test
    void assignLocalAdminBuildings_persistsRowAndEmitsAssignedOutbox() throws Exception {
        assignLocalAdminBuildingsUseCase.assignBuildings("admin-la", List.of(BUILDING_ID.id()));

        Integer bindings = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM local_admin_buildings WHERE user_id=? AND building_id=?",
                Integer.class, "admin-la", BUILDING_ID.id());
        assertThat(bindings).isEqualTo(1);

        Integer outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE event_type='LOCAL_ADMIN_BUILDING_ASSIGNED'",
                Integer.class);
        assertThat(outboxCount).isEqualTo(1);

        OutboxEvent assignedEvent = outboxEventRepository.findPending().stream()
                .filter(e -> "LOCAL_ADMIN_BUILDING_ASSIGNED".equals(e.getEventType()))
                .findFirst().orElseThrow();
        var payload = objectMapper.readTree(assignedEvent.getPayload());
        assertThat(payload.get("userId").asText()).isEqualTo("admin-la");
        assertThat(payload.get("buildingId").asText()).isEqualTo(BUILDING_ID.id());
        assertThat(payload.get("eventType").asText()).isEqualTo("LOCAL_ADMIN_BUILDING_ASSIGNED");
    }

    @Test
    void tournamentLifecycle_draftOpenCancel_persistsCancelledAndEmitsSummary() throws Exception {
        Instant now = Instant.now();
        TournamentId tId = new TournamentId("t-cancel");
        Tournament draft = new Tournament(tId, "Cancel Cup", GameType.CHESS, false, 1,
                TournamentFormat.SINGLE_ELIMINATION, TournamentStatus.DRAFT, now, null,
                new UserId("admin-la"), now);
        tournamentService.create(draft, List.of("b1", "b2"));
        assertThat(tournamentService.getById(tId).orElseThrow().status()).isEqualTo(TournamentStatus.DRAFT);

        tournamentService.open(tId);
        assertThat(tournamentService.getById(tId).orElseThrow().status()).isEqualTo(TournamentStatus.OPEN_REGISTRATION);

        TournamentDto cancelled = tournamentService.cancel(tId);
        assertThat(cancelled.status()).isEqualTo(TournamentStatus.CANCELLED);
        assertThat(tournamentService.getById(tId).orElseThrow().status()).isEqualTo(TournamentStatus.CANCELLED);

        Integer summaryOutbox = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE event_type='TOURNAMENT_SUMMARY_UPSERTED'",
                Integer.class);
        assertThat(summaryOutbox).isEqualTo(3);

        OutboxEvent cancelEvent = outboxEventRepository.findPending().stream()
                .filter(e -> "TOURNAMENT_SUMMARY_UPSERTED".equals(e.getEventType()))
                .filter(e -> {
                    try {
                        return "CANCELLED".equals(objectMapper.readTree(e.getPayload()).get("status").asText());
                    } catch (Exception ex) {
                        return false;
                    }
                })
                .findFirst().orElseThrow(() -> new AssertionError("no TOURNAMENT_SUMMARY_UPSERTED row with status=CANCELLED"));
        var cpayload = objectMapper.readTree(cancelEvent.getPayload());
        assertThat(cpayload.get("tournamentId").asText()).isEqualTo("t-cancel");
        assertThat(cpayload.get("status").asText()).isEqualTo("CANCELLED");
    }

    @Test
    void getStatistics_realAggregationService_readsSeededRow() {
        LocalDate periodStart = LocalDate.of(2026, 7, 1);
        LocalDate periodEnd = LocalDate.of(2026, 7, 31);
        jdbcTemplate.update(
                "INSERT INTO aggregated_statistics (id, building_id, game_type, period_start, period_end, "
                        + "total_sessions, avg_duration_seconds, total_reservations, total_aborted_sessions, data) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), BUILDING_ID.id(), "CHESS", periodStart, periodEnd,
                7, 240, 2, 1, null);

        List<StatisticsDto> result = statisticsAggregationService.getStatistics(BUILDING_ID,
                GameType.CHESS, periodStart, periodEnd);

        assertThat(result).hasSize(1);
        StatisticsDto dto = result.get(0);
        assertThat(dto.buildingId()).isEqualTo(BUILDING_ID.id());
        assertThat(dto.gameType()).isEqualTo("CHESS");
        assertThat(dto.totalSessions()).isEqualTo(7);
        assertThat(dto.totalAbortedSessions()).isEqualTo(1);
    }
}
