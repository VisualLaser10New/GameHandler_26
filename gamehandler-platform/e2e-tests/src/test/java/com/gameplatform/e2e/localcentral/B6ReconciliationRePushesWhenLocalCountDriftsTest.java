package com.gameplatform.e2e.localcentral;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.gameplatform.central.application.service.UserReplicationReconciliationService;
import com.gameplatform.central.application.service.UserService;
import com.gameplatform.e2e.harness.DualContextTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B6 — The periodic reconciliation service detects a count mismatch between the
 * central users table (3 users) and the local {@code replicated_users} table
 * (0 users), re-pushes the full central snapshot, and logs a
 * "Reconciliation mismatch" WARN.
 */
@DisplayName("B6: Reconciliation re-pushes when local count drifts")
class B6ReconciliationRePushesWhenLocalCountDriftsTest extends DualContextTestBase {

    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(UserReplicationReconciliationService.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void detachAppender() {
        if (logger != null && listAppender != null) {
            logger.detachAppender(listAppender);
            listAppender.stop();
        }
    }

    @Test
    @DisplayName("central=3, local=0 mismatch triggers re-push of 3 users and a WARN log")
    void reconciliationRePushesWhenLocalCountDrifts() {
        // 1. Register building-1 at central FIRST, while no users exist yet, so the
        //    M8 LateRegistrationCatchUp (triggered afterCommit on registration)
        //    replays zero users and leaves local replicated_users empty. Registering
        //    the building after the users (as this test originally did) replays those
        //    users via the catch-up, pre-empting the very drift this test wants to
        //    create. The central UserReplicationSchedulerService is disabled in the
        //    e2e config (app.sync-interval-ms=999999999), so user outbox events are
        //    NOT auto-pushed between registration and reconcile().
        registerBuildingAtCentral("building-1", "http://localhost:" + localPort);

        // 2. Register 3 users on central (outbox USER_REGISTERED events stay PENDING
        //    because the replication scheduler is disabled in e2e).
        centralBean(UserService.class).register("user0", "pw", "u0@x.com");
        centralBean(UserService.class).register("user1", "pw", "u1@x.com");
        centralBean(UserService.class).register("user2", "pw", "u2@x.com");

        // 3. Local should have 0 replicated users (nothing replicated yet)
        assertThat(localJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM replicated_users", Integer.class))
                .as("local has 0 replicated users before reconciliation")
                .isEqualTo(0);

        // 4. Trigger reconciliation — sees central=3, local=0 → mismatch → re-push all 3
        centralBean(UserReplicationReconciliationService.class).reconcile();

        // 5. Assert local now has 3 replicated users
        assertThat(localJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM replicated_users", Integer.class))
                .as("local has 3 replicated users after reconciliation re-push")
                .isEqualTo(3);

        // 6. Assert central log contains "Reconciliation mismatch"
        assertThat(listAppender.list)
                .filteredOn(ev -> ev.getLevel() == Level.WARN)
                .anySatisfy(ev -> assertThat(ev.getFormattedMessage())
                        .contains("Reconciliation mismatch")
                        .contains("buildingId=building-1"));
    }
}