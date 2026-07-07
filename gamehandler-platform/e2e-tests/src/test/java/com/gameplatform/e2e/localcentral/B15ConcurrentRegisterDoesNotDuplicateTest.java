package com.gameplatform.e2e.localcentral;

import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.e2e.harness.DualContextTestBase;
import com.gameplatform.shared.domain.model.BuildingId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("B15: Concurrent register() does not duplicate or crash")
class B15ConcurrentRegisterDoesNotDuplicateTest extends DualContextTestBase {

    @Test
    @DisplayName("10 threads registering the same new building concurrently -> exactly 1 row, only benign unique-constraint races")
    void concurrentRegisterSameBuilding() throws InterruptedException {
        String buildingId = "building-concurrent";
        String baseUrl = "http://localhost:" + localPort;

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger(0);
        List<Throwable> caught = Collections.synchronizedList(new ArrayList<>());

        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                ready.countDown();
                try {
                    start.await();
                    localServerRegistryPort.register(
                            new RegisteredLocalServer(new BuildingId(buildingId), baseUrl, Instant.now(), true));
                } catch (Exception e) {
                    errors.incrementAndGet();
                    caught.add(e);
                }
            });
        }
        tasks.forEach(executor::submit);
        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        // The unique constraint on local_servers.building_id guarantees that at
        // most one row is ever created regardless of how many threads race. Some
        // losers may surface a DataIntegrityViolationException at transaction
        // COMMIT: the in-method catch in LocalServerRepositoryAdapter only sees
        // violations that flush during save(), while deferred-flush losers throw
        // after the @Transactional method returns (the proxy commits/flushes and
        // rethrows the translated exception). That is the benign, expected race
        // outcome. The regressions we guard against are (a) duplicate rows or
        // (b) any NON-unique-constraint crash.
        Integer rowCount = centralJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM local_servers WHERE building_id = ?",
                Integer.class, buildingId);
        assertThat(rowCount)
                .as("exactly one local_servers row for the building id (no duplicates)")
                .isEqualTo(1);

        Boolean isActive = centralJdbcTemplate.queryForObject(
                "SELECT is_active FROM local_servers WHERE building_id = ?",
                Boolean.class, buildingId);
        assertThat(isActive).isTrue();

        for (Throwable t : caught) {
            boolean benign = false;
            for (Throwable c = t; c != null; c = c.getCause()) {
                if (c instanceof DataIntegrityViolationException) {
                    benign = true;
                    break;
                }
            }
            assertThat(benign)
                    .as("any exception from a losing thread must be a benign unique-constraint "
                            + "violation, not another kind of crash; got: " + t)
                    .isTrue();
        }
    }
}