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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalServerRepositoryAdapterRegisterTest {

    @Mock LocalServerJpaRepository jpaRepository;
    @Mock LateRegistrationCatchUpService lateRegistrationCatchUpService;

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

        // Re-registration (heartbeat path): catch-up must NOT fire.
        adapter.register(new RegisteredLocalServer(
                new BuildingId("building-1"), "https://local-1:8081", Instant.parse("2026-07-05T00:00:00Z"), true));

        verify(jpaRepository).save(any());
        verifyNoInteractions(lateRegistrationCatchUpService);
    }

    @Test
    void registerDoesNothingWhenServerIsNull() {
        LocalServerRepositoryAdapter adapter = new LocalServerRepositoryAdapter(jpaRepository, lateRegistrationCatchUpService);
        adapter.register(null);
        verifyNoInteractions(jpaRepository);
        verifyNoInteractions(lateRegistrationCatchUpService);
    }
}
