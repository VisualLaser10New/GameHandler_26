package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gameplatform.local.domain.exception.ConcurrentStateException;
import com.gameplatform.local.domain.model.Reservation;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.ReservationJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.ReservationMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.ReservationJpaRepository;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.ReservationId;
import com.gameplatform.shared.domain.model.ReservationStatus;
import com.gameplatform.shared.domain.model.UserId;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * POF-5 optimistic-locking guard at the {@link ReservationRepositoryAdapter}
 * boundary. Mockito-level twin of
 * {@link GameRepositoryAdapterOptimisticLockGuardTest} (see that class's javadoc
 * for the {@code @DataJpaTest}/MQTT rationale).
 *
 * <p><b>What IS verified here:</b></p>
 * <ol>
 *   <li>{@code mapper.toDomain} transports {@code entity.version} onto the domain
 *       {@link Reservation} (null {@code ->} {@code 0L}).</li>
 *   <li>{@code mapper.toEntity} sets {@code version=0L} for a brand-new domain
 *       ({@code version == 0}) and the domain version for an existing one
 *       ({@code version > 0}), so Spring Data uses merge (not persist).</li>
 *   <li>{@code adapter.save} delegates to {@code saveAndFlush} (not {@code save}).</li>
 *   <li>{@code adapter.save} translates a Spring
 *       {@link OptimisticLockingFailureException} thrown by {@code saveAndFlush}
 *       into a domain {@link ConcurrentStateException} carrying the original
 *       cause and a message naming the reservation id.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class ReservationRepositoryAdapterOptimisticLockGuardTest {

    private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant END = Instant.parse("2026-01-01T11:00:00Z");
    private static final Instant CREATED = Instant.parse("2026-01-01T09:00:00Z");

    @Mock private ReservationJpaRepository jpaRepository;

    private final ReservationMapper mapper = new ReservationMapper();

    private ReservationRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ReservationRepositoryAdapter(jpaRepository, mapper);
    }

    @Test
    @DisplayName("toDomain copies entity.version; a null entity.version falls back to 0L")
    void toDomainCopiesVersion() {
        ReservationJpaEntity persisted = new ReservationJpaEntity(
                "r-1", "g-1", "u-1", "PENDING", START, END, CREATED);
        persisted.setVersion(9L);

        Reservation domain = mapper.toDomain(persisted);
        assertThat(domain.getVersion()).isEqualTo(9L);

        ReservationJpaEntity fresh = new ReservationJpaEntity(
                "r-2", "g-1", "u-1", "PENDING", START, END, CREATED);
        assertThat(mapper.toDomain(fresh).getVersion()).isEqualTo(0L);
    }

    @Test
    @DisplayName("toEntity sets version=0L for a new domain (version==0) and the domain version for an existing one (version>0)")
    void toEntityVersionRoundTrip() {
        Reservation fresh = new Reservation(
                new ReservationId("r-1"), new GameId("g-1"), new UserId("u-1"),
                ReservationStatus.PENDING, START, END, CREATED);
        ReservationJpaEntity freshEntity = mapper.toEntity(fresh);
        assertThat(freshEntity.getVersion()).isEqualTo(0L);

        Reservation existing = new Reservation(
                new ReservationId("r-1"), new GameId("g-1"), new UserId("u-1"),
                ReservationStatus.PENDING, START, END, CREATED, 5L);
        ReservationJpaEntity existingEntity = mapper.toEntity(existing);
        assertThat(existingEntity.getVersion()).isEqualTo(5L);
    }

    @Test
    @DisplayName("save delegates to saveAndFlush and never to save")
    void saveDelegatesToSaveAndFlush() {
        Reservation existing = new Reservation(
                new ReservationId("r-1"), new GameId("g-1"), new UserId("u-1"),
                ReservationStatus.PENDING, START, END, CREATED, 1L);
        when(jpaRepository.saveAndFlush(any(ReservationJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        adapter.save(existing);

        verify(jpaRepository).saveAndFlush(any(ReservationJpaEntity.class));
        verify(jpaRepository, never()).save(any());
    }

    @Test
    @DisplayName("save wraps OptimisticLockingFailureException as ConcurrentStateException with the reservation id and original cause")
    void saveWrapsOptimisticLockFailure() {
        Reservation existing = new Reservation(
                new ReservationId("r-1"), new GameId("g-1"), new UserId("u-1"),
                ReservationStatus.PENDING, START, END, CREATED, 1L);
        when(jpaRepository.saveAndFlush(any(ReservationJpaEntity.class)))
                .thenThrow(new OptimisticLockingFailureException("stale"));

        assertThatThrownBy(() -> adapter.save(existing))
                .isInstanceOf(ConcurrentStateException.class)
                .hasMessageContaining("Concurrent modification of reservation")
                .hasMessageContaining("r-1")
                .hasCauseInstanceOf(OptimisticLockingFailureException.class);
    }
}