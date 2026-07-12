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
 */
class EventTypeContractTest {

    private static final Set<String> EXPECTED_EVENT_TYPES = Set.of(
            "USER_REGISTERED",
            "RESERVATION_CREATED",
            "RESERVATION_CANCELLED",
            "GAME_SESSION_COMPLETED",
            "GAME_SESSION_ABORTED",
            "TOURNAMENT_MATCH_COMPLETED"
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
