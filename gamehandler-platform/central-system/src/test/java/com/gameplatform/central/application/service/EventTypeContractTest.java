package com.gameplatform.central.application.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architectural invariant (FASE 6 step 5, plan line 215): every {@code eventType}
 * String literal emitted by the local-server outbox producers MUST be handled by
 * an explicit branch in {@code SyncEventProcessor.processEvent} on the central
 * side. Drift between producer and consumer is a class-of-bug that this test
 * forbids at compile/test time.
 *
 * <p>Implemented with pure reflection/file-scan — no ArchUnit dependency (per
 * plan line 263).</p>
 *
 * <h2>Source of truth for {@link #EXPECTED_EVENT_TYPES}</h2>
 * The set below was derived by grepping
 * {@code gamehandler-platform/local-server/src/main/java} for string literals
 * of the form {@code "GAME_SESSION_*"}, {@code "USER_*"}, {@code "RESERVATION_*"}.
 * The grep (FASE 6 step 5 context gathering) found these five literals, each
 * emitted by an outbox producer:
 * <ul>
 *   <li>{@code USER_REGISTERED}      — {@code LocalSignupService}</li>
 *   <li>{@code RESERVATION_CREATED}   — {@code ReservationService}</li>
 *   <li>{@code RESERVATION_CANCELLED} — {@code ReservationService}</li>
 *   <li>{@code GAME_SESSION_COMPLETED}— {@code GameSessionService}</li>
 *   <li>{@code GAME_SESSION_ABORTED}  — {@code HealthCheckService},
 *       {@code SessionRecoveryHelper}</li>
 * </ul>
 * Note: {@code USER_UPDATED} is intentionally excluded — it is emitted by the
 * <em>central</em> {@code UserService.updateUser} outbox, NOT by the local-server.
 * When a new event type is introduced in local-server, add it here and the
 * central processor must add a branch or this test will fail.
 *
 * <h2>FASE 7 §7.B W6/W9/W10/W12 — admin-request literals (batch S6)</h2>
 * The eight {@code *_REQUESTED} literals below are Local-emitted by the W use
 * cases (Local admin/PLAYER flows). Each one was verified to appear:
 * <ul>
 *   <li>{@code ROLE_ASSIGNMENT_REQUESTED}          — {@code AssignRoleRequestedService}</li>
 *   <li>{@code GAME_DEFINITION_UPSERT_REQUESTED}   — {@code UpsertGameDefinitionRequestedService}</li>
 *   <li>{@code TOURNAMENT_CREATE_REQUESTED}        — {@code CreateTournamentRequestedService}</li>
 *   <li>{@code TOURNAMENT_OPEN_REQUESTED}          — {@code TournamentLifecycleRequestedService}</li>
 *   <li>{@code TOURNAMENT_CANCEL_REQUESTED}        — {@code TournamentLifecycleRequestedService}</li>
 *   <li>{@code TOURNAMENT_SCHEDULE_REQUESTED}      — {@code TournamentLifecycleRequestedService}</li>
 *   <li>{@code TOURNAMENT_UPDATE_REQUESTED}        — {@code UpdateTournamentRequestedService}</li>
 *   <li>{@code TOURNAMENT_DELETE_REQUESTED}        — {@code DeleteTournamentRequestedService}</li>
 *   <li>{@code PARTICIPANT_REGISTER_REQUESTED}     — {@code RegisterTournamentParticipantRequestedService}</li>
 * </ul>
 * The matching Central branches in {@code SyncEventProcessor.processEvent} were
 * added in batch S3-A. The Central-emitted return events
 * ({@code USER_UPDATED}, {@code GAME_DEFINITION_UPSERTED},
 * {@code TOURNAMENT_SUMMARY_UPSERTED}, {@code TOURNAMENT_STANDINGS_UPSERTED},
 * {@code TOURNAMENT_PARTICIPANTS_UPSERTED}, {@code LOCAL_SERVER_REGISTRY_UPSERTED})
 * are produced by the Central outbox (not by the Local) and are validated by
 * the sibling {@code ReplicationEventTypeContractTest} (batch S6 gap S1 §16.7 A5).
 */
class EventTypeContractTest {

    private static final Set<String> EXPECTED_EVENT_TYPES = Set.of(
            "USER_REGISTERED",
            "RESERVATION_CREATED",
            "RESERVATION_CANCELLED",
            "GAME_SESSION_COMPLETED",
            "GAME_SESSION_ABORTED",
            "TOURNAMENT_MATCH_COMPLETED",
            "ROLE_ASSIGNMENT_REQUESTED",
            "GAME_DEFINITION_UPSERT_REQUESTED",
            "TOURNAMENT_CREATE_REQUESTED",
            "TOURNAMENT_OPEN_REQUESTED",
            "TOURNAMENT_CANCEL_REQUESTED",
            "TOURNAMENT_SCHEDULE_REQUESTED",
            "TOURNAMENT_UPDATE_REQUESTED",
            "TOURNAMENT_DELETE_REQUESTED",
            "PARTICIPANT_REGISTER_REQUESTED"
    );

    @Test
    void everyLocalEventTypeIsHandledByCentralProcessor() throws IOException {
        Path processorSrc = findSourceFile("SyncEventProcessor.java",
                projectRoot().resolve("central-system/src/main/java"));
        assertThat(Files.exists(processorSrc))
                .as("SyncEventProcessor.java must exist under central-system/src/main/java")
                .isTrue();
        String src = Files.readString(processorSrc);

        Set<String> missing = new TreeSet<>();
        for (String eventType : EXPECTED_EVENT_TYPES) {
            Pattern p = Pattern.compile("\"" + Pattern.quote(eventType) + "\"");
            if (!p.matcher(src).find()) {
                missing.add(eventType);
            }
        }
        assertThat(missing)
                .as("SyncEventProcessor.processEvent must contain an explicit branch for every eventType literal emitted by the local-server (source-of-truth: EXPECTED_EVENT_TYPES)")
                .isEmpty();
    }

    @Test
    void everyExpectedEventTypeIsEmittedByLocalServer() throws IOException {
        Path localMainRoot = projectRoot().resolve("local-server/src/main/java");
        assertThat(Files.exists(localMainRoot)).isTrue();
        String allLocalSrc;
        try (Stream<Path> walk = Files.walk(localMainRoot)) {
            allLocalSrc = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .map(p -> {
                        try { return Files.readString(p); }
                        catch (IOException e) { return ""; }
                    })
                    .collect(Collectors.joining("\n"));
        }
        Set<String> missing = new TreeSet<>();
        for (String eventType : EXPECTED_EVENT_TYPES) {
            Pattern p = Pattern.compile("\"" + Pattern.quote(eventType) + "\"");
            if (!p.matcher(allLocalSrc).find()) {
                missing.add(eventType);
            }
        }
        assertThat(missing)
                .as("Each eventType in EXPECTED_EVENT_TYPES must be emitted as a literal somewhere in local-server/src/main/java")
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
     * containing both {@code central-system/src/main/java} and
     * {@code local-server/src/main/java}. Surefire sets {@code user.dir} to
     * the module basedir (e.g. {@code gamehandler-platform/central-system}),
     * so this resolves to the {@code gamehandler-platform} directory regardless
     * of which module the test runs in.
     */
    private Path projectRoot() {
        Path p = Paths.get(System.getProperty("user.dir"));
        while (p != null) {
            if (Files.isDirectory(p.resolve("central-system/src/main/java"))
                    && Files.isDirectory(p.resolve("local-server/src/main/java"))) {
                return p;
            }
            p = p.getParent();
        }
        return Paths.get(System.getProperty("user.dir"));
    }
}
