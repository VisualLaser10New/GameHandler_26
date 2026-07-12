package com.gameplatform.central.application.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architectural invariant (FASE 7 §7.D batch S6, gap emerso in S1 §16.7 A5):
 * every {@code eventType} String literal drained by
 * {@link UserReplicationSchedulerService#isReplicationEvent} MUST be:
 * <ol>
 *   <li>(a) declared as a literal in {@code UserReplicationSchedulerService.java}
 *       (the schedulers' drain filter), AND</li>
 *   <li>(b) emitted as a literal by at least one Central outbox producer
 *       (the writers in the listed producer classes).</li>
 * </ol>
 *
 * <p>This is the symmetric guardian of {@code EventTypeContractTest}: the latter
 * pins Local-emitted literals to Central {@code SyncEventProcessor} branches;
 * this test pins Central-emitted literals to the replication scheduler drain AND
 * to the Central producers. Drift in either direction (a producer emits an event
 * the scheduler does not drain OR the scheduler drains an event nobody emits)
 * is a class-of-bug that this test forbids at compile/test time.</p>
 *
 * <p>Implemented with pure file-scan (no ArchUnit dependency) – same shape as
 * {@code EventTypeContractTest}.</p>
 *
 * <h2>Source of truth for {@link #EXPECTED_REPLICATION_EVENT_TYPES}</h2>
 * The ten literals below were derived by reading the
 * {@code isReplicationEvent/is*Event} helpers in
 * {@code UserReplicationSchedulerService.java} (lines 966-1005 at S6):
 * <ul>
 *   <li>{@code USER_REGISTERED}              — {@code UserService.register}</li>
 *   <li>{@code USER_UPDATED}                  — {@code UserService.updateUser}</li>
 *   <li>{@code LOCAL_ADMIN_BUILDING_ASSIGNED} — {@code LocalAdminBuildingService}</li>
 *   <li>{@code LOCAL_ADMIN_BUILDING_REVOKED}  — {@code LocalAdminBuildingService}</li>
 *   <li>{@code GAME_DEFINITION_UPSERTED}      — {@code GameDefinitionService}</li>
 *   <li>{@code TOURNAMENT_MATCH_SCHEDULED}    — {@code TournamentMatchOutboxAdapter}</li>
 *   <li>{@code TOURNAMENT_SUMMARY_UPSERTED}   — {@code TournamentService}</li>
 *   <li>{@code TOURNAMENT_STANDINGS_UPSERTED} — {@code TournamentStandingsService}</li>
 *   <li>{@code TOURNAMENT_PARTICIPANTS_UPSERTED} — {@code TournamentRegistrationService}</li>
 *   <li>{@code LOCAL_SERVER_REGISTRY_UPSERTED}  — {@code LocalServerRepositoryAdapter}</li>
 * </ul>
 * These are Central-emitted and pushed to Local servers (replication feed). They
 * do NOT belong in {@code EventTypeContractTest.EXPECTED_EVENT_TYPES} because
 * that test pins Local-emitted → Central-consumer.
 */
class ReplicationEventTypeContractTest {

    private static final Set<String> EXPECTED_REPLICATION_EVENT_TYPES = Set.of(
            "USER_REGISTERED",
            "USER_UPDATED",
            "LOCAL_ADMIN_BUILDING_ASSIGNED",
            "LOCAL_ADMIN_BUILDING_REVOKED",
            "GAME_DEFINITION_UPSERTED",
            "TOURNAMENT_MATCH_SCHEDULED",
            "TOURNAMENT_SUMMARY_UPSERTED",
            "TOURNAMENT_STANDINGS_UPSERTED",
            "TOURNAMENT_PARTICIPANTS_UPSERTED",
            "LOCAL_SERVER_REGISTRY_UPSERTED"
    );

    /**
     * Central outbox producer classes that emit the drained replication event
     * types. The test concatenates the source of these files and verifies each
     * literal appears at least once. Note: {@code UserReplicationSchedulerService}
     * itself is intentionally excluded from this list — it drains, does not
     * produce. {@code LateRegistrationCatchUpService} is also excluded because
     * it re-drains (consumes), not produces.
     */
    private static final List<String> PRODUCER_FILES = List.of(
            "UserService.java",
            "LocalAdminBuildingService.java",
            "GameDefinitionService.java",
            "TournamentService.java",
            "TournamentStandingsService.java",
            "TournamentRegistrationService.java",
            "TournamentMatchOutboxAdapter.java",
            "LocalServerRepositoryAdapter.java"
    );

    @Test
    void everyReplicationEventTypeIsDrainedByScheduler() throws IOException {
        Path schedulerSrc = findSourceFile("UserReplicationSchedulerService.java",
                projectRoot().resolve("central-system/src/main/java"));
        assertThat(Files.exists(schedulerSrc))
                .as("UserReplicationSchedulerService.java must exist under central-system/src/main/java")
                .isTrue();
        String src = Files.readString(schedulerSrc);

        Set<String> missing = new TreeSet<>();
        for (String eventType : EXPECTED_REPLICATION_EVENT_TYPES) {
            Pattern p = Pattern.compile("\"" + Pattern.quote(eventType) + "\"");
            if (!p.matcher(src).find()) {
                missing.add(eventType);
            }
        }
        assertThat(missing)
                .as("UserReplicationSchedulerService.isReplicationEvent must drain every Central-emitted replication eventType (source-of-truth: EXPECTED_REPLICATION_EVENT_TYPES)")
                .isEmpty();
    }

    @Test
    void everyReplicationEventTypeIsEmittedByCentralProducer() throws IOException {
        Path centralMainRoot = projectRoot().resolve("central-system/src/main/java");
        assertThat(Files.exists(centralMainRoot)).isTrue();

        StringBuilder allProducersSrc = new StringBuilder();
        for (String filename : PRODUCER_FILES) {
            Path producerFile = findSourceFile(filename, centralMainRoot);
            assertThat(Files.exists(producerFile))
                    .as("Producer file %s must exist under central-system/src/main/java", filename)
                    .isTrue();
            allProducersSrc.append(Files.readString(producerFile)).append("\n");
        }
        String producersSrc = allProducersSrc.toString();

        Set<String> missing = new TreeSet<>();
        for (String eventType : EXPECTED_REPLICATION_EVENT_TYPES) {
            Pattern p = Pattern.compile("\"" + Pattern.quote(eventType) + "\"");
            if (!p.matcher(producersSrc).find()) {
                missing.add(eventType);
            }
        }
        assertThat(missing)
                .as("Each eventType in EXPECTED_REPLICATION_EVENT_TYPES must be emitted as a literal somewhere in the Central producer files (%s)", PRODUCER_FILES)
                .isEmpty();
    }

    private Path findSourceFile(String filename, Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(filename))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("File " + filename + " not found under " + root));
        }
    }

    /**
     * Walk up from the test JVM working directory until we find a directory
     * containing {@code central-system/src/main/java}. Surefire sets
     * {@code user.dir} to the module basedir (e.g.
     * {@code gamehandler-platform/central-system}), so this resolves to the
     * {@code gamehandler-platform} directory regardless of which module the
     * test runs in. Mirrors {@code EventTypeContractTest.projectRoot()}.
     */
    private Path projectRoot() {
        Path p = Paths.get(System.getProperty("user.dir"));
        while (p != null) {
            if (Files.isDirectory(p.resolve("central-system/src/main/java"))) {
                return p;
            }
            p = p.getParent();
        }
        return Paths.get(System.getProperty("user.dir"));
    }
}
