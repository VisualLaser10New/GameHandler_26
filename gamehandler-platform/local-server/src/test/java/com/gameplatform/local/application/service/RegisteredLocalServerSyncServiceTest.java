package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gameplatform.local.domain.model.RegisteredLocalServerLocal;
import com.gameplatform.local.domain.ports.out.AdminRequestRepository;
import com.gameplatform.local.domain.ports.out.RegisteredLocalServerLocalRepository;
import com.gameplatform.shared.dto.LocalServerRegistryEventDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

/**
 * Pure-Mockito unit tests for {@link RegisteredLocalServerSyncService}
 * (PIANO §7.B): upsert by buildingId; {@code markCompleted} when
 * {@code originatingRequestId != null}.
 */
@ExtendWith(MockitoExtension.class)
class RegisteredLocalServerSyncServiceTest {

    private static final Instant UPDATED_AT = Instant.parse("2026-07-12T10:00:00Z");

    @Mock RegisteredLocalServerLocalRepository registeredLocalServerLocalRepository;
    @Mock AdminRequestRepository adminRequestRepository;

    private final Clock clock = Clock.fixed(UPDATED_AT, ZoneId.of("UTC"));
    private RegisteredLocalServerSyncService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new RegisteredLocalServerSyncService(
                registeredLocalServerLocalRepository, adminRequestRepository, clock);
    }

    private LocalServerRegistryEventDto serverDto(String buildingId, String originatingRequestId, boolean active) {
        return new LocalServerRegistryEventDto("evt-" + buildingId, "LOCAL_SERVER_REGISTRY_UPSERTED",
                buildingId, "https://" + buildingId + ":8081", UPDATED_AT, active, originatingRequestId, UPDATED_AT);
    }

    @Test
    void applyEvents_upsertsSingleEvent() {
        LocalServerRegistryEventDto dto = serverDto("building-1", null, true);

        service.applyEvents(List.of(dto));

        ArgumentCaptor<RegisteredLocalServerLocal> captor = ArgumentCaptor.forClass(RegisteredLocalServerLocal.class);
        verify(registeredLocalServerLocalRepository).save(captor.capture());
        RegisteredLocalServerLocal saved = captor.getValue();
        assertEquals("building-1", saved.getBuildingId().id());
        assertEquals("https://building-1:8081", saved.getBaseUrl());
        assertTrue(saved.isActive());
    }

    @Test
    void applyEvents_idempotentOnRedelivery() {
        LocalServerRegistryEventDto dto = serverDto("building-1", null, true);

        service.applyEvents(List.of(dto));
        service.applyEvents(List.of(dto));

        verify(registeredLocalServerLocalRepository, times(2)).save(any(RegisteredLocalServerLocal.class));
    }

    @Test
    void applyEvents_twoEventsForDifferentBuildings() {
        LocalServerRegistryEventDto d1 = serverDto("building-1", null, true);
        LocalServerRegistryEventDto d2 = serverDto("building-2", null, false);

        service.applyEvents(List.of(d1, d2));

        verify(registeredLocalServerLocalRepository, times(2)).save(any(RegisteredLocalServerLocal.class));
    }

    @Test
    void applyEvents_nullListIsNoOp() {
        service.applyEvents(null);
        verifyNoInteractions(registeredLocalServerLocalRepository);
        verifyNoInteractions(adminRequestRepository);
    }

    @Test
    void applyEvents_skipsNullEvents() {
        service.applyEvents(Arrays.asList((LocalServerRegistryEventDto) null));
        verifyNoInteractions(registeredLocalServerLocalRepository);
        verifyNoInteractions(adminRequestRepository);
    }

    @Test
    void applyEvents_unknownEventTypeSkipped() {
        LocalServerRegistryEventDto dto = new LocalServerRegistryEventDto("evt-1", "FOO",
                "building-1", "https://local:8081", UPDATED_AT, true, null, UPDATED_AT);

        service.applyEvents(List.of(dto));

        verifyNoInteractions(registeredLocalServerLocalRepository);
        verifyNoInteractions(adminRequestRepository);
    }

    @Test
    void applyEvents_blankBuildingIdSkipped() {
        LocalServerRegistryEventDto dto = new LocalServerRegistryEventDto("evt-1", "LOCAL_SERVER_REGISTRY_UPSERTED",
                " ", "https://local:8081", UPDATED_AT, true, null, UPDATED_AT);

        service.applyEvents(List.of(dto));

        verifyNoInteractions(registeredLocalServerLocalRepository);
        verifyNoInteractions(adminRequestRepository);
    }

    @Test
    void applyEvents_markCompletedWhenOriginatingRequestIdNotNull() {
        LocalServerRegistryEventDto dto = serverDto("building-1", "req-1", true);

        service.applyEvents(List.of(dto));

        verify(adminRequestRepository).markCompleted(eq("req-1"), argThat(s -> s.contains("applied")), any());
    }

    @Test
    void applyEvents_originatingRequestIdNull_skipsMarkCompleted() {
        LocalServerRegistryEventDto dto = serverDto("building-1", null, true);

        service.applyEvents(List.of(dto));

        verifyNoInteractions(adminRequestRepository);
    }
}
