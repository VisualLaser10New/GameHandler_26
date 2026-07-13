package com.gameplatform.e2e.localcentral;

import com.gameplatform.central.application.service.TournamentService;
import com.gameplatform.central.application.service.UserReplicationSchedulerService;
import com.gameplatform.central.domain.model.GameDefinition;
import com.gameplatform.central.domain.model.Tournament;
import com.gameplatform.central.domain.ports.out.GameDefinitionRepository;
import com.gameplatform.e2e.harness.DualContextTestBase;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentFormat;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.domain.model.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C1 — cross-module tournament-create propagation. A DRAFT tournament created
 * on the central {@link TournamentService} emits a PENDING
 * {@code TOURNAMENT_SUMMARY_UPSERTED} outbox event; the replication scheduler
 * drains it and pushes the summary to the registered local server, whose
 * {@code TournamentSummaryLocalSyncService} upserts a row into
 * {@code tournaments_summary_local}. Mirrors the B-series user-replication e2e
 * tests but for the FASE 7-A2 tournament-summary projection.
 */
@DisplayName("C1: Central tournament create propagates TOURNAMENT_SUMMARY_UPSERTED to local")
class C1TournamentCreatePropagatesToLocalTest extends DualContextTestBase {

    @Test
    @DisplayName("create(tournament) -> outbox TOURNAMENT_SUMMARY_UPSERTED -> local tournaments_summary_local row (DRAFT)")
    void createPropagatesSummaryToLocal() {
        Instant now = Instant.now();
        centralBean(GameDefinitionRepository.class).save(
                new GameDefinition(GameType.CHESS, "Chess", 2, 2, false, null, now, now));
        registerBuildingAtCentral("building-1", "http://localhost:" + localPort);

        Tournament draft = new Tournament(new TournamentId("t-c1"), "Cup C1", GameType.CHESS, false, 1,
                TournamentFormat.SINGLE_ELIMINATION, TournamentStatus.DRAFT, now, null,
                new UserId("admin-1"), now);
        centralBean(TournamentService.class).create(draft, List.of("building-1", "building-2"));

        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE status='PENDING' AND event_type='TOURNAMENT_SUMMARY_UPSERTED'",
                Integer.class))
                .as("central outbox has 1 PENDING TOURNAMENT_SUMMARY_UPSERTED event")
                .isEqualTo(1);

        centralBean(UserReplicationSchedulerService.class).replicateUsers();

        assertThat(localJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tournaments_summary_local WHERE tournament_id='t-c1'",
                Integer.class))
                .as("local tournaments_summary_local has the tournament summary row")
                .isEqualTo(1);
        assertThat(localJdbcTemplate.queryForObject(
                "SELECT status FROM tournaments_summary_local WHERE tournament_id='t-c1'",
                String.class))
                .as("local summary status mirrors the central DRAFT")
                .isEqualTo("DRAFT");
    }
}