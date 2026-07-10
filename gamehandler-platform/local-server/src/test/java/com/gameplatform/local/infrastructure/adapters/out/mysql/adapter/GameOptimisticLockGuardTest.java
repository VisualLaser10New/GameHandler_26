package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gameplatform.local.domain.exception.ConcurrentStateException;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.GameJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.GameMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.GameJpaRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.domain.model.GameType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * POF-5 optimistic-locking guard at the {@link GameRepositoryAdapter} boundary.
 *
 * <p>Mirrors the Mockito-level approach of
 * {@code UserRepositoryAdapterOrderingGuardIT}: a real {@link GameMapper} plus a
 * mocked {@link GameJpaRepository}. A real {@code @DataJpaTest} slice is blocked
 * by the local-server's eager MQTT-client initialisation (see the rationale in
 * {@code UserRepositoryAdapterOrderingGuardIT}'s javadoc), so the live Hibernate
 * {@code @Version} UPDATE...WHERE version=? behaviour is not re-asserted here; it
 * is covered e2e by {@code B17ConcurrentGameMachineStartOptimisticLockTest}.</p>
 *
 * <p><b>What IS verified here:</b></p>
 * <ol>
 *   <li>{@code mapper.toDomain} transports {@code entity.version} onto the domain
 *       {@link Game} (null {@code ->} {@code 0L}).</li>
 *   <li>{@code mapper.toEntity} sets {@code version=0L} for a brand-new domain
 *       ({@code version == 0}) and the domain version for an existing one
 *       ({@code version > 0}), so Spring Data uses merge (not persist).</li>
 *   <li>{@code adapter.save} delegates to {@code saveAndFlush} (not {@code save}).</li>
 *   <li>{@code adapter.save} translates a Spring
 *       {@link OptimisticLockingFailureException} thrown by {@code saveAndFlush}
 *       into a domain {@link ConcurrentStateException} carrying the original
 *       cause and a message naming the game id.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class GameOptimisticLockGuardTest {

    @Mock private GameJpaRepository jpaRepository;

    private final GameMapper mapper = new GameMapper();

    private GameRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new GameRepositoryAdapter(jpaRepository, mapper);
    }

    @Test
    @DisplayName("toDomain copies entity.version; a null entity.version falls back to 0L")
    void toDomainCopiesVersion() {
        GameJpaEntity persisted = new GameJpaEntity(
                "g-1", "CHESS", "Chess", "b-1", GameMachineStatus.AVAILABLE);
        persisted.setVersion(7L);

        Game domain = mapper.toDomain(persisted);
        assertThat(domain.getVersion()).isEqualTo(7L);

        GameJpaEntity fresh = new GameJpaEntity(
                "g-2", "CHESS", "Chess", "b-2", GameMachineStatus.AVAILABLE);
        assertThat(mapper.toDomain(fresh).getVersion()).isEqualTo(0L);
    }

    @Test
    @DisplayName("toEntity sets version=0L for a new domain (version==0) and the domain version for an existing one (version>0)")
    void toEntityVersionRoundTrip() {
        Game fresh = new Game(new GameId("g-1"), GameType.CHESS, "Chess",
                new BuildingId("b-1"), GameMachineStatus.AVAILABLE);
        GameJpaEntity freshEntity = mapper.toEntity(fresh);
        assertThat(freshEntity.getVersion()).isEqualTo(0L);

        Game existing = new Game(new GameId("g-1"), GameType.CHESS, "Chess",
                new BuildingId("b-1"), GameMachineStatus.AVAILABLE, 5L);
        GameJpaEntity existingEntity = mapper.toEntity(existing);
        assertThat(existingEntity.getVersion()).isEqualTo(5L);
    }

    @Test
    @DisplayName("save delegates to saveAndFlush and never to save")
    void saveDelegatesToSaveAndFlush() {
        Game existing = new Game(new GameId("g-1"), GameType.CHESS, "Chess",
                new BuildingId("b-1"), GameMachineStatus.AVAILABLE, 1L);
        when(jpaRepository.saveAndFlush(any(GameJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        adapter.save(existing);

        verify(jpaRepository).saveAndFlush(any(GameJpaEntity.class));
        verify(jpaRepository, never()).save(any());
    }

    @Test
    @DisplayName("save wraps OptimisticLockingFailureException as ConcurrentStateException with the game id and original cause")
    void saveWrapsOptimisticLockFailure() {
        Game existing = new Game(new GameId("g-1"), GameType.CHESS, "Chess",
                new BuildingId("b-1"), GameMachineStatus.AVAILABLE, 1L);
        when(jpaRepository.saveAndFlush(any(GameJpaEntity.class)))
                .thenThrow(new OptimisticLockingFailureException("stale"));

        assertThatThrownBy(() -> adapter.save(existing))
                .isInstanceOf(ConcurrentStateException.class)
                .hasMessageContaining("Concurrent modification of game")
                .hasMessageContaining("g-1")
                .hasCauseInstanceOf(OptimisticLockingFailureException.class);
    }
}