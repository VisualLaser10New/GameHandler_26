package com.gameplatform.central.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.model.OutboxEvent;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.adapter.OutboxEventRepositoryAdapter;
import com.gameplatform.shared.dto.OutboxEventDto;
import com.gameplatform.shared.dto.SyncPayloadDto;
import com.gameplatform.shared.dto.UserSyncDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Multi-building end-to-end integration test.
 *
 * <p>Extends {@link MultiBuildingTestBase}, which boots the full central Spring
 * context against H2 (MODE=MySQL) <b>without</b> mocking
 * {@code LocalServerRegistryPort} — so the real H2-backed
 * {@code LocalServerRepositoryAdapter} is used, registrations persist into
 * {@code local_servers}, and {@link com.gameplatform.central.application.service.UserReplicationSchedulerService#replicateUsers()}
 * sees the registered servers and pushes to real WireMock stubs.</p>
 *
 * <p>Four scenarios covering the multi-building contract:</p>
 * <ol>
 *   <li><b>scenario1_bothBuildingsSelfRegister</b> — registering building-2 and
 *       building-3 through the real registry persists both rows; the M8
 *       {@code afterCommit} catch-up runs against an empty outbox (no-op) and
 *       does NOT throw; no {@code PUT /internal/users/sync} is fired (outbox
 *       empty).</li>
 *   <li><b>scenario2_userRegisteredAtBuilding2ReplicatedToBuildings1And3</b> —
 *       with buildings 1/2/3 active, a {@code USER_REGISTERED} outbox event is
 *       replicated by {@code replicateUsers()}. <em>Actual scheduler semantics
 *       (discovered by reading {@code UserReplicationSchedulerService}): the
 *       scheduler pushes to ALL active servers — there is NO source-building
 *       skip.</em> So the event reaches building-1, building-2 (the source) AND
 *       building-3: 3 {@code replication_progress} rows, 3 WireMock PUT calls,
 *       event marked SENT. The plan's wording ("replicated to building-1 AND
 *       building-3") implied a source-skip that does NOT exist; the assertion
 *       count is adjusted to match the actual all-active push and the
 *       discrepancy is documented here.</li>
 *   <li><b>scenario3_completedSessionsProduceDistinctAggregatedStatistics</b> —
 *       {@code GAME_SESSION_COMPLETED} sync events for building-2/CHESS and
 *       building-3/FOOSBALL produce two DISTINCT {@code aggregated_statistics}
 *       rows keyed by {@code (building_id, game_type, period_start)} (the C-R1/S3
 *       distinct-row invariant). No cross-building pollution: no
 *       building-2/FOOSBALL or building-3/CHESS row.</li>
 *   <li><b>scenario4_resendSameUserRegisteredIsIdempotent</b> — re-invoking
 *       {@code replicateUsers()} after the event is already SENT does NOT
 *       trigger a second push. <em>Actual dedup mechanism (discovered by reading
 *       the scheduler): {@code OutboxEventRepository.findPendingLimit(limit)}
 *       fetches only {@code PENDING} rows, so an already-SENT event is never
 *       re-fetched and never re-pushed. This OUTBOX STATUS FILTER is the primary
 *       idempotency guard for the central→local replication path.</em> The
 *       {@code processed_events(event_id PK)} dedup is for the
 *       LOCAL→CENTRAL inbound sync path ({@code SyncEventProcessor.processOne})
 *       and does not apply to a central outbox {@code USER_REGISTERED} event.
 *       Secondary guards — {@code replication_progress(event_id, server_id)}
 *       unique PK via {@code existsByEventIdAndServerId} pre-check + DIVE catch
 *       (C-R5) — would prevent a duplicate progress insert if a PENDING
 *       duplicate were ever re-sent; this test asserts the primary
 *       outbox-status-filter path.</li>
 * </ol>
 *
 * <p><b>H2 outbox-payload JSON caveat (and the test-only workaround):</b> the
 * central {@code outbox_events} table declares {@code payload} as a native
 * {@code JSON} column ({@code @Column(columnDefinition="JSON") String payload}).
 * The central scheduler and the M8 catch-up deserialize the payload back via
 * {@code objectMapper.readValue(entity.getPayload(), UserSyncDto.class)}. On H2
 * in MODE=MySQL, binding a plain {@code String} to a {@code JSON} column (and
 * reading it back via {@code ResultSet.getString}) round-trips as a
 * <em>double-encoded JSON string literal</em> — e.g. the written
 * {@code {"userId":"u-1",...}} is read back as
 * {@code "{\"userId\":\"u-1\",...}"}, so Jackson sees a STRING scalar, not an
 * object, and the scheduler marks the event FAILED instead of pushing it. A
 * raw-JDBC {@code CAST(? AS JSON)} insert does NOT cure this (H2's
 * {@code getString} on a JSON column re-wraps the value on read regardless of
 * how it was stored). This is the same H2-vs-MySQL divergence documented on
 * {@code LateRegistrationCatchUpProgressPersistenceIT} (which downgraded its
 * catch-up IT to Mockito for that reason).</p>
 *
 * <p>Rather than downgrade scenarios 2 and 4 to Mockito, this IT keeps the REAL
 * scheduler, the REAL H2-backed registry, the REAL {@code LocalServerRestAdapter}
 * (hitting WireMock) and the REAL H2-backed {@code replication_progress}
 * adapter, and installs a tiny test-only {@code @Primary OutboxEventRepository}
 * shim ({@link CleanPayloadOutbox}) that <b>delegates every method</b> to the
 * real {@link OutboxEventRepositoryAdapter} and only post-processes
 * {@code findPending()} / {@code findPendingLimit(int)} to unwrap the one
 * JSON-string-scalar layer H2 adds on read. This makes the scheduler's
 * outbox→{@code UserSyncDto} deserialization succeed on H2 exactly as it does
 * against MySQL in production, so the full push→progress→SENT contract is
 * exercised end-to-end. No production file is touched; the {@code UserSyncDto}
 * payload is serialized with the very same autowired {@code ObjectMapper} the
 * production scheduler uses, and the shim is a pure read-side adapter (writes
 * go straight to the real adapter / H2).</p>
 */
@DisplayName("Multi-building end-to-end IT — registration, replication, stats, idempotency")
@Import(MultiBuildingEndToEndIT.CleanPayloadOutbox.class)
class MultiBuildingEndToEndIT extends MultiBuildingTestBase {

    @Test
    @DisplayName("Scenario 1: buildings 2 & 3 self-register; registry persists both; no push fired (empty outbox)")
    void scenario1_bothBuildingsSelfRegister() {
        // Registering through the REAL LocalServerRegistryPort (H2-backed) — the M8
        // afterCommit catch-up runs inside register()'s @Transactional proxy commit.
        // Outbox is empty → catch-up is a documented no-op and must not throw.
        registerBuilding("building-2", baseUrl2);
        registerBuilding("building-3", baseUrl3);

        List<String> registeredBuildingIds = localServerRegistryPort.findAll().stream()
                .map(s -> s.getBuildingId().id())
                .toList();
        assertThat(registeredBuildingIds)
                .as("findAll() must return both registered buildings, backed by the real H2 adapter")
                .contains("building-2", "building-3");

        // Empty outbox → the registration-time catch-up pushed nothing. Assert NO
        // PUT /internal/users/sync was served on any building prefix.
        assertThat(putSyncCountFor(SYNC_PATH_1)).isZero();
        assertThat(putSyncCountFor(SYNC_PATH_2)).isZero();
        assertThat(putSyncCountFor(SYNC_PATH_3)).isZero();
    }

    @Test
    @DisplayName("Scenario 2: USER_REGISTERED event replicated to all active buildings via replicateUsers() (no source-skip)")
    void scenario2_userRegisteredAtBuilding2ReplicatedToBuildings1And3() throws Exception {
        // Three active buildings. Registrations happen against an EMPTY outbox, so
        // the M8 afterCommit catch-up at registration time is a no-op for all three.
        registerBuilding("building-1", baseUrl1);
        registerBuilding("building-2", baseUrl2);
        registerBuilding("building-3", baseUrl3);

        // Seed a central→local USER_REGISTERED outbox event at building-2 (the
        // source-building is only a logical notion; the scheduler does not skip it).
        String eventId = UUID.randomUUID().toString();
        String payload = objectMapper.writeValueAsString(new UserSyncDto(
                "u-multi-2", "alice", "a-multi@example.com", "hash-multi",
                List.of("PLAYER"), Instant.parse("2026-07-05T10:00:00Z")));
        insertPendingUserOutboxEvent(eventId, payload);

        // Invoke the scheduler directly — synchronous (allOf().join()), so by the
        // time the call returns every parallel per-server push has settled.
        userReplicationSchedulerService.replicateUsers();

        // ACTUAL scheduler semantics: pushes to ALL active servers — there is no
        // source-building skip. With 3 active buildings → 3 replication_progress rows.
        Integer progressRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM replication_progress WHERE event_id = ?",
                Integer.class, eventId);
        assertThat(progressRows)
                .as("scheduler pushes to every active server (no source-skip) → 3 progress rows")
                .isEqualTo(3);

        // Event is fully acknowledged by all servers → marked SENT.
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM outbox_events WHERE id = ?", String.class, eventId);
        assertThat(status).isEqualTo("SENT");

        // Each building's WireMock stub received exactly one PUT /internal/users/sync.
        assertThat(putSyncCountFor(SYNC_PATH_1)).isEqualTo(1);
        assertThat(putSyncCountFor(SYNC_PATH_2)).isEqualTo(1);
        assertThat(putSyncCountFor(SYNC_PATH_3)).isEqualTo(1);
    }

    @Test
    @DisplayName("Scenario 3: completed sessions produce DISTINCT aggregated_statistics rows per (building, gameType, period)")
    void scenario3_completedSessionsProduceDistinctAggregatedStatistics() {
        // Two active buildings; the sync-receive path updates lastSeenAt at the end
        // of receiveSyncPayload, so each building must be registered.
        registerBuilding("building-2", baseUrl2);
        registerBuilding("building-3", baseUrl3);

        // building-2 → CHESS, building-3 → FOOSBALL. Same UTC day → same period.
        sendSessionCompleted("building-2", "CHESS");
        sendSessionCompleted("building-3", "FOOSBALL");

        // C-R1/S3 distinct-row invariant: one row per (building, gameType, period).
        Integer chessB2Total = jdbcTemplate.queryForObject(
                "SELECT total_sessions FROM aggregated_statistics "
                        + "WHERE building_id = 'building-2' AND game_type = 'CHESS'",
                Integer.class);
        assertThat(chessB2Total).isEqualTo(1);

        Integer foosB3Total = jdbcTemplate.queryForObject(
                "SELECT total_sessions FROM aggregated_statistics "
                        + "WHERE building_id = 'building-3' AND game_type = 'FOOSBALL'",
                Integer.class);
        assertThat(foosB3Total).isEqualTo(1);

        // No cross-building pollution: the events sent were building-2/CHESS and
        // building-3/FOOSBALL only — there must be NO building-2/FOOSBALL or
        // building-3/CHESS row (tables were wiped in baseSetUp).
        Integer foosB2Rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM aggregated_statistics "
                        + "WHERE building_id = 'building-2' AND game_type = 'FOOSBALL'",
                Integer.class);
        assertThat(foosB2Rows)
                .as("no cross-building pollution: no building-2/FOOSBALL row")
                .isZero();

        Integer chessB3Rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM aggregated_statistics "
                        + "WHERE building_id = 'building-3' AND game_type = 'CHESS'",
                Integer.class);
        assertThat(chessB3Rows)
                .as("no cross-building pollution: no building-3/CHESS row")
                .isZero();
    }

    @Test
    @DisplayName("Scenario 4: re-invoking replicateUsers() after the event is SENT is idempotent (no second push)")
    void scenario4_resendSameUserRegisteredIsIdempotent() throws Exception {
        registerBuilding("building-1", baseUrl1);
        registerBuilding("building-2", baseUrl2);
        registerBuilding("building-3", baseUrl3);

        // Seed a PENDING USER_REGISTERED event with a fresh eventId.
        String eventId = UUID.randomUUID().toString();
        String payload = objectMapper.writeValueAsString(new UserSyncDto(
                "u-multi-4", "bob", "b-multi@example.com", "hash-multi",
                List.of("PLAYER"), Instant.parse("2026-07-05T10:00:00Z")));
        insertPendingUserOutboxEvent(eventId, payload);

        // First run: pushes the PENDING event to all 3 active servers → 3 progress
        // rows, event transitions to SENT.
        userReplicationSchedulerService.replicateUsers();

        int firstB1 = putSyncCountFor(SYNC_PATH_1);
        int firstB2 = putSyncCountFor(SYNC_PATH_2);
        int firstB3 = putSyncCountFor(SYNC_PATH_3);
        Integer firstProgress = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM replication_progress WHERE event_id = ?",
                Integer.class, eventId);
        String firstStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM outbox_events WHERE id = ?", String.class, eventId);

        assertThat(firstB1).isEqualTo(1);
        assertThat(firstB2).isEqualTo(1);
        assertThat(firstB3).isEqualTo(1);
        assertThat(firstProgress).isEqualTo(3);
        assertThat(firstStatus).isEqualTo("SENT");

        // Second run: the event is now SENT, so findPendingLimit(50) (which
        // selects only PENDING rows) does NOT re-fetch it → no second push, no new
        // replication_progress insert, status unchanged. This outbox-status-filter
        // IS the primary idempotency guard for the central→local replication path.
        userReplicationSchedulerService.replicateUsers();

        int secondB1 = putSyncCountFor(SYNC_PATH_1);
        int secondB2 = putSyncCountFor(SYNC_PATH_2);
        int secondB3 = putSyncCountFor(SYNC_PATH_3);
        Integer secondProgress = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM replication_progress WHERE event_id = ?",
                Integer.class, eventId);
        String secondStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM outbox_events WHERE id = ?", String.class, eventId);

        assertThat(secondB1)
                .as("SENT event is not re-fetched by findPendingLimit → no duplicate push to building-1")
                .isEqualTo(firstB1);
        assertThat(secondB2)
                .as("no duplicate push to building-2")
                .isEqualTo(firstB2);
        assertThat(secondB3)
                .as("no duplicate push to building-3")
                .isEqualTo(firstB3);
        assertThat(secondProgress)
                .as("replication_progress row count unchanged after the second (no-op) run")
                .isEqualTo(firstProgress);
        assertThat(secondStatus).isEqualTo("SENT");
    }

    /**
     * Sends a {@code GAME_SESSION_COMPLETED} sync event for the given building
     * and gameType through the real {@link com.gameplatform.central.domain.ports.in.ReceiveSyncDataUseCase}
     * — the same entry point {@code SyncController} invokes on
     * {@code POST /internal/sync/receive}. The payload shape matches the contract
     * exercised by {@code EndToEndSimulationIT}: event id, occurredAt, sessionId,
     * gameType, durationSeconds, status.
     */
    private void sendSessionCompleted(String buildingId, String gameType) {
        String eventId = UUID.randomUUID().toString();
        String occurredAt = "2026-07-05T12:00:00Z";
        String payload = "{\"eventId\":\"" + eventId + "\","
                + "\"occurredAt\":\"" + occurredAt + "\","
                + "\"sessionId\":\"sess-" + gameType.toLowerCase() + "\","
                + "\"gameType\":\"" + gameType + "\","
                + "\"durationSeconds\":120,"
                + "\"status\":\"COMPLETED\","
                + "\"resultJson\":null}";
        OutboxEventDto event = new OutboxEventDto(
                eventId, "GAME_SESSION_COMPLETED", payload, Instant.parse(occurredAt));
        SyncPayloadDto batch = new SyncPayloadDto(buildingId, List.of(event));
        receiveSyncDataUseCase.receiveSyncPayload(batch);
    }

    /**
     * Inserts a PENDING {@code USER_REGISTERED} outbox row via raw JDBC.
     *
     * <p>The payload is bound as a plain String parameter. On H2 the
     * {@code outbox_events.payload} JSON column double-encodes the value on
     * read-back (see the class-level H2 caveat), so the read-side unwrapping is
     * handled by the {@link CleanPayloadOutbox} shim that wraps the real
     * {@link OutboxEventRepositoryAdapter}. The insert itself is a plain row
     * insert — {@code CAST(? AS JSON)} was empirically confirmed NOT to cure the
     * read-back double-encoding, so it is not used.</p>
     */
    private void insertPendingUserOutboxEvent(String eventId, String payloadJson) {
        jdbcTemplate.update(
                "INSERT INTO outbox_events (id, event_type, payload, status, created_at, sent_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                eventId,
                "USER_REGISTERED",
                payloadJson,
                "PENDING",
                Timestamp.from(Instant.parse("2026-07-05T10:00:00Z")),
                null);
    }

    /**
     * Test-only {@code @Primary} {@link OutboxEventRepository} that delegates
     * every method to the real {@link OutboxEventRepositoryAdapter} bean and
     * post-processes only the two read methods the scheduler/catch-up use to
     * fetch pending events ({@code findPending()} / {@code findPendingLimit(int)}),
     * unwrapping the single JSON-string-scalar layer H2 adds when reading back a
     * {@code JSON} column bound as a plain {@code String}.
     *
     * <p>This is a pure read-side adapter — {@code save}, {@code markAsSent},
     * {@code markAsFailed} and {@code countPendingReplicationForServer} go
     * straight to the real adapter / H2. It exists solely to let the real
     * scheduler's {@code objectMapper.readValue(payload, UserSyncDto.class)} run
     * end-to-end on H2 the same way it runs on MySQL in production, without
     * touching any production file. Injecting the concrete
     * {@link OutboxEventRepositoryAdapter} (rather than the interface) avoids any
     * circular dependency with the {@code @Primary} override.</p>
     */
    @TestConfiguration
    static class CleanPayloadOutbox {

        @Bean
        @Primary
        OutboxEventRepository cleanPayloadOutboxRepository(
                OutboxEventRepositoryAdapter realAdapter, ObjectMapper objectMapper) {
            return new OutboxEventRepository() {
                @Override
                public OutboxEvent save(OutboxEvent event) {
                    return realAdapter.save(event);
                }

                @Override
                public List<OutboxEvent> findPending() {
                    return unwrap(realAdapter.findPending(), objectMapper);
                }

                @Override
                public List<OutboxEvent> findPendingLimit(int limit) {
                    return unwrap(realAdapter.findPendingLimit(limit), objectMapper);
                }

                @Override
                public void markAsSent(String id) {
                    realAdapter.markAsSent(id);
                }

                @Override
                public void markAsFailed(String id) {
                    realAdapter.markAsFailed(id);
                }

                @Override
                public long countPendingReplicationForServer(String serverId) {
                    return realAdapter.countPendingReplicationForServer(serverId);
                }

                /**
                 * Unwraps the JSON-string-scalar layer H2 adds on read-back. A
                 * clean payload (already a JSON object text, starts with
                 * {@code '{'}) is passed through untouched; a double-encoded
                 * payload (starts with {@code '"'}) is parsed as a Jackson
                 * {@code TextNode} and its textual content returned.
                 */
                private List<OutboxEvent> unwrap(List<OutboxEvent> events, ObjectMapper objectMapper) {
                    if (events == null || events.isEmpty()) {
                        return events;
                    }
                    return events.stream()
                            .map(e -> {
                                String p = e.getPayload();
                                if (p == null || p.isEmpty() || p.charAt(0) != '"') {
                                    return e;
                                }
                                try {
                                    String clean = objectMapper.readTree(p).asText();
                                    return new OutboxEvent(
                                            e.getId(), e.getEventType(), clean,
                                            e.getStatus(), e.getCreatedAt(), e.getSentAt());
                                } catch (Exception ex) {
                                    return e;
                                }
                            })
                            .toList();
                }
            };
        }
    }
}
