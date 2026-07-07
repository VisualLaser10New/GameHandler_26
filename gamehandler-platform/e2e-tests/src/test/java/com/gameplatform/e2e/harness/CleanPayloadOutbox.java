package com.gameplatform.e2e.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.model.OutboxEvent;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.adapter.OutboxEventRepositoryAdapter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * Test-only {@code @Primary} {@link OutboxEventRepository} that delegates every
 * method to the real {@link OutboxEventRepositoryAdapter} bean and post-processes
 * only the two read methods the scheduler/catch-up use to fetch pending events
 * ({@code findPending()} / {@code findPendingLimit(int)}), unwrapping the single
 * JSON-string-scalar layer H2 adds when reading back a {@code JSON} column bound
 * as a plain {@code String}.
 *
 * <p>This is a pure read-side adapter — {@code save}, {@code markAsSent},
 * {@code markAsFailed} and {@code countPendingReplicationForServer} go straight
 * to the real adapter / H2. It exists solely to let the real scheduler's
 * {@code objectMapper.readValue(payload, UserSyncDto.class)} run end-to-end on
 * H2 the same way it runs on MySQL in production, without touching any
 * production file. Injecting the concrete {@link OutboxEventRepositoryAdapter}
 * (rather than the interface) avoids any circular dependency with the
 * {@code @Primary} override.</p>
 *
 * <p>Adapted from {@code MultiBuildingEndToEndIT.CleanPayloadOutbox} in the
 * central-system test suite.</p>
 */
@TestConfiguration
public class CleanPayloadOutbox {

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
