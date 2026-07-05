package com.gameplatform.central.integration;

import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B17d — Schema alignment test. Boots the central-system Spring context against the H2
 * in-memory database (profile "test", {@code ddl-auto=create-drop}). Hibernate generates
 * the schema from the JPA {@code @Entity} classes, so the columns present in H2 are exactly
 * those the JPA entities declare. This test asserts that the entity-generated schema
 * contains the columns the production {@code init.sql} (see
 * {@code infrastructure/mysql-central/init.sql}) defines — i.e. that the JPA mapping stays
 * aligned with the production DDL.
 *
 * <p>Specifically it covers the audit gap's most critical alignment points:
 * <ul>
 *   <li>{@code aggregated_statistics.total_aborted_sessions} — the column introduced to
 *       separate aborted-session counts from completed-session counts (must not regress).</li>
 *   <li>{@code processed_events.event_id} — the deduplication primary key.</li>
 *   <li>{@code outbox_events.payload}/{@code status}/{@code event_type} — the JSON payload
 *       + status + type columns the central outbox relies on.</li>
 *   <li>{@code users.password_hash}/{@code email}/{@code roles} — auth-column alignment.</li>
 *   <li>{@code local_servers.base_url}/{@code last_seen_at}/{@code is_active} — registry-
 *       table alignment.</li>
 * </ul>
 *
 * <p><b>Note on {@code retry_count}:</b> {@code infrastructure/mysql-central/init.sql}
 * declares {@code outbox_events.retry_count INT DEFAULT 0}, but the central
 * {@link com.gameplatform.central.infrastructure.adapters.out.mysql.entity.OutboxEventJpaEntity}
 * intentionally does NOT map {@code retry_count} — retry/failed-threshold tracking is a
 * local-server concern (see
 * {@link com.gameplatform.local.infrastructure.adapters.out.mysql.entity.OutboxEventJpaEntity},
 * which DOES declare {@code retry_count}). The central outbox never re-tries, so the column
 * is unused on the central side and is therefore NOT asserted here. The local-server side
 * schema alignment (including {@code retry_count}) is exercised implicitly by any
 * local-server integration test that reads/writes the {@code outbox_events} table.</p>
 *
 * <p><b>Local-server schema:</b> a dedicated local-server SchemaAlignmentTest is not added
 * here because booting the local-server {@code @SpringBootApplication} eagerly instantiates
 * {@code MqttConfig.mqttClient} (calls {@code client.connect()} against
 * {@code tcp://localhost:1883}), which fails without an MQTT broker in CI. The plan's scope
 * ("this single test in central-system is sufficient for B17d") is respected.</p>
 *
 * <p>The {@link LocalServerRegistryPort} is mocked so the central context boots without
 * attempting outbound REST to non-existent local servers (matches the pattern in
 * {@link ContractTestBase}).</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class SchemaAlignmentTest {

    @MockBean
    private LocalServerRegistryPort localServerRegistryPort;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("B17d: JPA entity-generated H2 schema contains the columns declared in mysql-central/init.sql")
    void centralSchemaHasExpectedColumns() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            DatabaseMetaData md = c.getMetaData();

            // aggregated_statistics — the key audit column (aborted-session separation).
            assertThat(columns(md, "aggregated_statistics"))
                    .contains("total_aborted_sessions",
                            "avg_duration_seconds",
                            "total_sessions",
                            "total_reservations",
                            "building_id",
                            "game_type",
                            "period_start",
                            "period_end");

            // processed_events — dedup PK.
            assertThat(columns(md, "processed_events"))
                    .contains("event_id", "processed_at");

            // outbox_events — payload + status + type. retry_count intentionally omitted (see class javadoc).
            assertThat(columns(md, "outbox_events"))
                    .contains("payload", "status", "event_type", "created_at", "sent_at");

            // users — auth columns.
            assertThat(columns(md, "users"))
                    .contains("password_hash", "email", "roles", "username");

            // local_servers — registry columns.
            assertThat(columns(md, "local_servers"))
                    .contains("base_url", "last_seen_at", "is_active", "building_id");
        }
    }

    /**
     * Returns the lowercased column names of the given table from the H2 catalog.
     * H2 runs in {@code MODE=MySQL;DATABASE_TO_LOWER=TRUE} (see application-test.yml),
     * so table/column identifiers are stored lowercase; results are lowercased for a
     * case-insensitive comparison against the lowercase expected names above.
     */
    private List<String> columns(DatabaseMetaData md, String table) throws SQLException {
        // Pass the table name in lowercase as declared by the @Table(name=...) annotations;
        // H2 with DATABASE_TO_LOWER=TRUE stores unquoted identifiers lowercase.
        try (ResultSet rs = md.getColumns(null, null, table, null)) {
            List<String> out = new ArrayList<>();
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                if (name != null) {
                    out.add(name.toLowerCase());
                }
            }
            return out;
        }
    }
}
