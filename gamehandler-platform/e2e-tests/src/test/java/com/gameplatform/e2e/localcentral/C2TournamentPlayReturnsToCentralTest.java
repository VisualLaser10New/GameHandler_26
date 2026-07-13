package com.gameplatform.e2e.localcentral;

import com.gameplatform.central.application.service.TournamentBracketService;
import com.gameplatform.central.application.service.TournamentRegistrationService;
import com.gameplatform.central.application.service.TournamentService;
import com.gameplatform.central.application.service.UserReplicationSchedulerService;
import com.gameplatform.central.domain.model.GameDefinition;
import com.gameplatform.central.domain.model.Tournament;
import com.gameplatform.central.domain.model.User;
import com.gameplatform.central.domain.ports.out.GameDefinitionRepository;
import com.gameplatform.central.domain.ports.out.UserRepository;
import com.gameplatform.e2e.harness.DualContextTestBase;
import com.gameplatform.local.application.service.GameSessionService;
import com.gameplatform.local.application.service.SyncSchedulerService;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentFormat;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;
import com.gameplatform.shared.domain.result.ChessResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * C2 — tournament schedule + match play round-trip. Builds the full single-
 * elimination round-1 on central (create -> open -> register 4 participants ->
 * schedule), replicates the SCHEDULED match routed to building-1 to the local,
 * plays it through the real local {@link GameSessionService} (start + end with
 * a winner), syncs the resulting {@code GAME_SESSION_COMPLETED} +
 * {@code TOURNAMENT_MATCH_COMPLETED} outbox rows back to central, and asserts
 * the central {@link TournamentBracketService#advanceWinner} created the
 * round-2 parent match (bracket advance).
 *
 * <p><b>Scope note:</b> the second round-1 match is routed (round-robin by
 * {@code bracketPosition} in {@code replicateTournamentMatchEvent}) to
 * building-2, which is not a real local in this test, so only the building-1
 * match is played here. Full tournament completion (match 2 + the round-2
 * final) is covered at the unit level by
 * {@code central-system/src/test/java/com/gameplatform/central/application/service/TournamentFlowEndToEndIT.java};
 * this e2e test focuses on the novel cross-module schedule -> replicate -> play
 * -> bracket-advance round-trip for the building-1 leg, which is not present in
 * the central-only IT.</p>
 */
@DisplayName("C2: tournament schedule + match play round-trip advances central bracket")
class C2TournamentPlayReturnsToCentralTest extends DualContextTestBase {

    @Test
    @DisplayName("create -> open -> register 4 -> schedule -> replicate -> play match 1 -> sync -> central round-2 parent created")
    void scheduleAndPlayAdvancesCentralBracket() {
        Instant now = Instant.now();
        centralBean(GameDefinitionRepository.class).save(
                new GameDefinition(GameType.CHESS, "Chess", 2, 2, false, null, now, now));
        UserRepository userRepository = centralBean(UserRepository.class);
        for (String uid : List.of("u1", "u2", "u3", "u4")) {
            userRepository.save(new User(new UserId(uid), uid, "hash", uid + "@x.com", List.of("PLAYER"), now));
        }

        registerBuildingAtCentral("building-1", "http://localhost:" + localPort);

        TournamentId tId = new TournamentId("t-c2");
        Tournament draft = new Tournament(tId, "Cup C2", GameType.CHESS, false, 1,
                TournamentFormat.SINGLE_ELIMINATION, TournamentStatus.DRAFT, now, null,
                new UserId("u1"), now);
        centralBean(TournamentService.class).create(draft, List.of("building-1", "building-2"));
        centralBean(TournamentService.class).open(tId);
        TournamentRegistrationService registrationService = centralBean(TournamentRegistrationService.class);
        for (String uid : List.of("u1", "u2", "u3", "u4")) {
            registrationService.register(tId, new UserId(uid), null, null);
        }
        centralBean(TournamentBracketService.class).schedule(tId);

        // Drain the FASE 7 replication events (TOURNAMENT_SUMMARY_UPSERTED x2,
        // TOURNAMENT_PARTICIPANTS_UPSERTED x4, TOURNAMENT_MATCH_SCHEDULED x2 —
        // only the match routed to building-1 reaches the real local; the
        // building-2 match stays PENDING because building-2 is not active).
        centralBean(UserReplicationSchedulerService.class).replicateUsers();

        assertThat(localJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tournaments_summary_local WHERE tournament_id='t-c2'",
                Integer.class))
                .as("local has the tournament summary (IN_PROGRESS after schedule)")
                .isEqualTo(1);
        assertThat(localJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tournament_participants_local WHERE tournament_id='t-c2'",
                Integer.class))
                .as("local has all 4 participants")
                .isEqualTo(4);

        // Match routed round-robin to building-1 is bracketPosition 1
        // (index 0 -> buildingIds[0]=building-1); bracketPosition 2 routes to
        // building-2 (not active) so only one round-1 SCHEDULED match replicates.
        assertThat(localJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tournament_matches_local WHERE tournament_id='t-c2' AND status='SCHEDULED'",
                Integer.class))
                .as("local building-1 has the 1 round-1 SCHEDULED match routed to it")
                .isEqualTo(1);

        String matchId = localJdbcTemplate.queryForObject(
                "SELECT id FROM tournament_matches_local WHERE tournament_id='t-c2' AND status='SCHEDULED'",
                String.class);
        String gameId = localJdbcTemplate.queryForObject(
                "SELECT game_id FROM tournament_matches_local WHERE id=?", String.class, matchId);
        String participantA = localJdbcTemplate.queryForObject(
                "SELECT participant_a FROM tournament_matches_local WHERE id=?", String.class, matchId);
        String participantB = localJdbcTemplate.queryForObject(
                "SELECT participant_b FROM tournament_matches_local WHERE id=?", String.class, matchId);

        // Seed the local game machine (the replicated match carries a fresh
        // gameId assigned round-robin by the central drain) and the local game
        // definition so GameSessionService validates the individual 2-player match.
        localJdbcTemplate.update(
                "INSERT INTO game_catalog (id, game_type, name, building_id, status) VALUES (?, ?, ?, ?, ?)",
                gameId, "CHESS", "Chess Table C2", "building-1", "AVAILABLE");
        localJdbcTemplate.update(
                "INSERT INTO game_definitions_local (game_type, name, min_players, max_players, team_allowed, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                "CHESS", "Chess", 2, 2, false);

        GameSessionService gameSessionService = localBean(GameSessionService.class);
        var session = gameSessionService.start(new GameId(gameId), GameType.CHESS,
                List.of(new UserId(participantA), new UserId(participantB)),
                null, new TournamentMatchId(matchId));
        assertThat(session.getTournamentMatchId()).isEqualTo(new TournamentMatchId(matchId));

        gameSessionService.end(session.getId(),
                new ChessResult(new UserId(participantA), List.of(new UserId(participantA)),
                        "checkmate", "8/8/8/8/8/8/8/8 w - - 0 1", WinCondition.WIN));

        assertThat(localJdbcTemplate.queryForObject(
                "SELECT status FROM tournament_matches_local WHERE id=?", String.class, matchId))
                .as("local match flipped to COMPLETED after endMatch")
                .isEqualTo("COMPLETED");
        assertThat(localJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE event_type='TOURNAMENT_MATCH_COMPLETED'",
                Integer.class))
                .as("local outbox emitted TOURNAMENT_MATCH_COMPLETED")
                .isEqualTo(1);
        assertThat(localJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE event_type='GAME_SESSION_COMPLETED'",
                Integer.class))
                .as("local outbox emitted GAME_SESSION_COMPLETED")
                .isEqualTo(1);

        // Sync the local outbox back to central; central SyncEventProcessor
        // handles TOURNAMENT_MATCH_COMPLETED -> advanceWinner creates the
        // round-2 parent with participantA = the winner.
        localBean(SyncSchedulerService.class).syncWithCentral();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(centralJdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM tournament_matches WHERE tournament_id='t-c2' AND round=2",
                        Integer.class))
                        .as("central round-2 parent match created by advanceWinner")
                        .isEqualTo(1));
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT participant_a FROM tournament_matches WHERE tournament_id='t-c2' AND round=2",
                String.class))
                .as("round-2 parent participantA is the winner of match 1")
                .isEqualTo(participantA);
    }
}