package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;

import com.gameplatform.central.application.service.LateRegistrationCatchUpService;
import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.RegisteredLocalServerJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.LocalServerJpaRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class LocalServerRepositoryAdapterRegisterTest {

    @Mock LocalServerJpaRepository jpaRepository;
    @Mock LateRegistrationCatchUpService lateRegistrationCatchUpService;

    @BeforeEach
    void setUp() {
        // M8: LocalServerRepositoryAdapter.register defers the catch-up to a registered
        // TransactionSynchronization.afterCommit(); registerSynchronization() requires an
        // active transaction-synchronization context. In production the @Transactional
        // register method provides it; in this pure Mockito unit test we mimic the IT
        // (LateRegistrationCatchUpProgressPersistenceIT) and initialize the context here.
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void registerCreatesNewEntityWhenAbsent() {
        LocalServerRepositoryAdapter adapter = new LocalServerRepositoryAdapter(jpaRepository, lateRegistrationCatchUpService);
        when(jpaRepository.findById("building-1")).thenReturn(Optional.empty());
        when(jpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        adapter.register(new RegisteredLocalServer(
                new BuildingId("building-1"), "https://local-1:8081", Instant.parse("2026-01-01T00:00:00Z"), true));

        ArgumentCaptor<RegisteredLocalServerJpaEntity> captor = ArgumentCaptor.forClass(RegisteredLocalServerJpaEntity.class);
        verify(jpaRepository).save(captor.capture());
        RegisteredLocalServerJpaEntity saved = captor.getValue();
        assertThat(saved.getBuildingId()).isEqualTo("building-1");
        assertThat(saved.getBaseUrl()).isEqualTo("https://local-1:8081");
        assertThat(saved.isActive()).isTrue();
        // M8: the catch-up is registered as an afterCommit synchronization, not invoked
        // synchronously with register(). No real tx commits in this unit test, so drive
        // the registered synchronization's afterCommit() manually to simulate the commit
        // callback and assert the catch-up is wired to fire post-commit.
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        // First registration must trigger late-registration catch-up.
        verify(lateRegistrationCatchUpService).catchUpNewlyRegisteredServer(any(RegisteredLocalServer.class));
    }

    @Test
    void registerIsIdempotentAndUpdatesExistingEntity() {
        LocalServerRepositoryAdapter adapter = new LocalServerRepositoryAdapter(jpaRepository, lateRegistrationCatchUpService);
        RegisteredLocalServerJpaEntity existing = new RegisteredLocalServerJpaEntity(
                "building-1", "https://old-url:8081", Instant.parse("2025-01-01T00:00:00Z"), false);
        when(jpaRepository.findById("building-1")).thenReturn(Optional.of(existing));
        when(jpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Instant now = Instant.parse("2026-07-05T12:00:00Z");
        adapter.register(new RegisteredLocalServer(
                new BuildingId("building-1"), "https://new-url:8081", now, true));

        // same entity instance updated in place (upsert, no duplicate row)
        assertThat(existing.getBaseUrl()).isEqualTo("https://new-url:8081");
        assertThat(existing.getLastSeenAt()).isEqualTo(now);
        assertThat(existing.isActive()).isTrue();
        verify(jpaRepository).save(existing);
        verify(jpaRepository, times(1)).save(any());
    }

    @Test
    void registerDoesNotInvokeCatchUpOnReRegistration() {
        LocalServerRepositoryAdapter adapter = new LocalServerRepositoryAdapter(jpaRepository, lateRegistrationCatchUpService);
        RegisteredLocalServerJpaEntity existing = new RegisteredLocalServerJpaEntity(
                "building-1", "https://local-1:8081", Instant.parse("2026-01-01T00:00:00Z"), true);
        when(jpaRepository.findById("building-1")).thenReturn(Optional.of(existing));
        when(jpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Re-registration while still active (heartbeat path): catch-up must NOT fire.
        // The server never went through a deactivation window, so any events it missed
        // while briefly unreachable stayed PENDING (pushes failed -> not marked SENT)
        // and will be retried by the 5-min UserReplicationSchedulerService.
        adapter.register(new RegisteredLocalServer(
                new BuildingId("building-1"), "https://local-1:8081", Instant.parse("2026-07-05T00:00:00Z"), true));

        verify(jpaRepository).save(any());
        verifyNoInteractions(lateRegistrationCatchUpService);
    }

    @Test
    void registerInvokesCatchUpOnReRegistrationWhenInactive() {
        LocalServerRepositoryAdapter adapter = new LocalServerRepositoryAdapter(jpaRepository, lateRegistrationCatchUpService);
        // Existing row that was deactivated by LocalServerHealthMonitorService after a
        // stale window (is_active=false). Re-registration flips it back to active.
        RegisteredLocalServerJpaEntity existing = new RegisteredLocalServerJpaEntity(
                "building-1", "https://local-1:8081", Instant.parse("2026-01-01T00:00:00Z"), false);
        when(jpaRepository.findById("building-1")).thenReturn(Optional.of(existing));
        when(jpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Reactivation: catch-up MUST fire. While this server was deactivated, any
        // USER_REGISTERED/USER_UPDATED events that were pushed to the OTHER active
        // servers were marked SENT without a replication_progress row for THIS server.
        // Only catch-up (which replays SENT+PENDING events lacking a progress row for
        // this building) can deliver them — the 5-min replication scheduler only reads
        // PENDING events, so it would never retry the already-SENT ones.
        adapter.register(new RegisteredLocalServer(
                new BuildingId("building-1"), "https://local-1:8081", Instant.parse("2026-07-05T00:00:00Z"), true));

        verify(jpaRepository).save(any());
        // Drive the registered afterCommit synchronization to simulate the tx commit
        // and assert the catch-up is wired to fire post-commit on reactivation.
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(lateRegistrationCatchUpService).catchUpNewlyRegisteredServer(any(RegisteredLocalServer.class));
    }

    @Test
    void registerDoesNothingWhenServerIsNull() {
        LocalServerRepositoryAdapter adapter = new LocalServerRepositoryAdapter(jpaRepository, lateRegistrationCatchUpService);
        adapter.register(null);
        verifyNoInteractions(jpaRepository);
        verifyNoInteractions(lateRegistrationCatchUpService);
    }
}
