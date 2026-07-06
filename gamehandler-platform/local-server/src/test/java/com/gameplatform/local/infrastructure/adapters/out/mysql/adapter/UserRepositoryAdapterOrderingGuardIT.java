package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.UserJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.UserMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.LocalUserJpaRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.UserJpaRepository;
import com.gameplatform.shared.domain.model.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * R2 — adapter-level wiring for the replicated-users ordering-guard columns.
 *
 * <p><b>Originally specified as a {@code @DataJpaTest} slice IT</b> asserting the
 * persisted {@code event_time}/{@code version} rows after stale/newer saves and
 * forcing an {@code OptimisticLockException}. The local-server's
 * {@code @SpringBootApplication} triggers eager {@code MqttConfig.mqttClient}
 * instantiation (connect to {@code tcp://localhost:1883}) during context
 * refresh, which fails in the CI/dev environment (no broker) — the very same
 * documented constraint already noted in
 * {@code OutboxEventBulkUpdateAtomicityTest} and
 * {@code SessionAbortHelperTest} javadocs. A custom minimal JPA-slice Spring
 * configuration to bypass MQTT was considered but rejected for the same
 * maintenance-burden rationale. Per plan step 16/19, this falls back to a
 * Mockito variant.</p>
 *
 * <p><b>What IS verified here (Mockito-level):</b></p>
 * <ol>
 *   <li>{@code findById(UserId)} delegates to {@code userJpaRepository.findById(String)}
 *       and maps the entity's {@code eventTime}/{@code updatedAt} back onto the
 *       domain {@code User} (the precondition the {@link UserSyncService}
 *       ordering guard relies on to read the persisted event time).</li>
 *   <li>{@code save} transports {@code eventTime}/{@code updatedAt} from the
 *       domain {@code User} onto the JPA entity and leaves {@code version} null
 *       on write (Hibernate assigns the initial {@code 0} on insert and
 *       increments it on subsequent updates — that is the defence-in-depth
 *       backstop for the cross-tx case).</li>
 *   <li>{@code findById(null)} returns {@code Optional.empty()} defensively.</li>
 * </ol>
 *
 * <p><b>What is NOT re-asserted here (documented gap):</b> the live Hibernate
 * {@code @Version} behaviour — {@code UPDATE ... WHERE version=?} producing an
 * {@code OptimisticLockException} / {@code ObjectOptimisticLockingFailureException}
 * on a stale-version concurrent commit — requires a real DB slice and therefore
 * cannot be exercised in this repo's CI (see MQTT constraint above). The
 * {@code @Version} mapping itself is present on {@link UserJpaEntity} and is
 * Hibernate's contract for the guard; the authoritative application-level guard
 * is the event-time check in {@link UserSyncService} (see
 * {@code UserSyncServiceOrderingGuardTest}).</p>
 */
@ExtendWith(MockitoExtension.class)
class UserRepositoryAdapterOrderingGuardIT {

    private static final Instant T0 = Instant.parse("2026-07-06T10:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-07-06T11:30:00Z");

    @Mock private UserJpaRepository jpaRepository;
    @Mock private LocalUserJpaRepository localUserJpaRepository;

    private final UserMapper mapper = new UserMapper();

    private UserRepositoryAdapter adapter;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        adapter = new UserRepositoryAdapter(jpaRepository, localUserJpaRepository, mapper);
    }

    @Test
    @DisplayName("findById maps eventTime + updatedAt from the entity back to the domain User")
    void findByIdCarriesEventTimeAndUpdatedAt() {
        UserJpaEntity entity = new UserJpaEntity(
                "u-1", "alice", "hash", "a@example.com", "PLAYER", T0, UPDATED);
        entity.setVersion(2L);

        when(jpaRepository.findById("u-1")).thenReturn(Optional.of(entity));

        Optional<User> found = adapter.findById(new UserId("u-1"));

        assertThat(found).isPresent();
        assertThat(found.get().getEventTime()).isEqualTo(T0);
        assertThat(found.get().getUpdatedAt()).isEqualTo(UPDATED);
        // Deprecated alias still reflects the canonical event time.
        @SuppressWarnings("deprecation")
        Instant deprecated = found.get().getSyncedAt();
        assertThat(deprecated).isEqualTo(T0);
    }

    @Test
    @DisplayName("findById returns empty for a missing user id")
    void findByIdMissingReturnsEmpty() {
        when(jpaRepository.findById("u-missing")).thenReturn(Optional.empty());
        assertThat(adapter.findById(new UserId("u-missing"))).isEmpty();
    }

    @Test
    @DisplayName("findById returns empty defensively for a null UserId")
    void findByIdNullReturnsEmpty() {
        assertThat(adapter.findById(null)).isEmpty();
        verify(jpaRepository, never()).findById(any());
    }

    @Test
    @DisplayName("save transports eventTime + updatedAt onto the entity and leaves version null for Hibernate")
    void saveTransportsEventTimeAndUpdatedAt() {
        User domain = new User(
                new UserId("u-1"), "alice", "hash-new", "a@example.com", List.of("PLAYER"), T0, UPDATED);

        UserJpaEntity persistedBack = new UserJpaEntity(
                "u-1", "alice", "hash-new", "a@example.com", "PLAYER", T0, UPDATED);
        persistedBack.setVersion(1L);
        when(jpaRepository.save(any(UserJpaEntity.class))).thenAnswer(inv -> {
            UserJpaEntity e = inv.getArgument(0);
            assertThat(e.getEventTime()).isEqualTo(T0);
            assertThat(e.getUpdatedAt()).isEqualTo(UPDATED);
            // synced_at kept in sync with event_time for back-compat external readers.
            assertThat(e.getSyncedAt()).isEqualTo(T0);
            // version MUST be null on write — Hibernate assigns/increments it.
            assertThat(e.getVersion()).isNull();
            return persistedBack;
        });

        User result = adapter.save(domain);

        assertThat(result).isNotNull();
        assertThat(result.getEventTime()).isEqualTo(T0);
        assertThat(result.getUpdatedAt()).isEqualTo(UPDATED);
        verify(jpaRepository).save(any(UserJpaEntity.class));
    }
}
