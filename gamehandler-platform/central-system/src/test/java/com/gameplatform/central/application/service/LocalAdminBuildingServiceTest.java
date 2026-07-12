package com.gameplatform.central.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gameplatform.central.domain.exception.UserNotFoundException;
import com.gameplatform.central.domain.model.LocalAdminBuilding;
import com.gameplatform.central.domain.model.OutboxEvent;
import com.gameplatform.central.domain.model.OutboxEventStatus;
import com.gameplatform.central.domain.model.User;
import com.gameplatform.central.domain.ports.out.LocalAdminBuildingRepository;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.central.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.LocalAdminBuildingEventDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LocalAdminBuildingService}, covering the assign / revoke
 * idempotency contract, outbox event emission, the UserNotFoundException path and
 * the query use case. Pure Mockito (no Spring context).
 */
@ExtendWith(MockitoExtension.class)
class LocalAdminBuildingServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-12T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @Mock
    private LocalAdminBuildingRepository localAdminBuildingRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private LocalAdminBuildingService service;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        service = new LocalAdminBuildingService(
                localAdminBuildingRepository, userRepository, outboxEventRepository,
                objectMapper, FIXED_CLOCK);
    }

    private User existingUser(String id) {
        return new User(new UserId(id), "admin-" + id, "$2a$10$hashplaceholder12345678901234567890",
                "admin-" + id + "@example.com", List.of("LOCAL_ADMIN"), Instant.parse("2026-01-01T00:00:00Z"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // assignBuildings()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void assignBuildings_createsBindingsAndWritesOutboxEvents_forNewBuildingsOnly() throws Exception {
        UserId uid = new UserId("u-1");
        when(userRepository.findById(uid)).thenReturn(Optional.of(existingUser("u-1")));
        // b1 already bound → idempotent skip; b2, b3 are new → save + outbox.
        when(localAdminBuildingRepository.existsByUserIdAndBuildingId(uid, new BuildingId("b1")))
                .thenReturn(true);
        when(localAdminBuildingRepository.existsByUserIdAndBuildingId(uid, new BuildingId("b2")))
                .thenReturn(false);
        when(localAdminBuildingRepository.existsByUserIdAndBuildingId(uid, new BuildingId("b3")))
                .thenReturn(false);
        when(localAdminBuildingRepository.save(any(LocalAdminBuilding.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.assignBuildings("u-1", List.of("b1", "b2", "b3"));

        // b1 skipped; only b2 and b3 saved.
        ArgumentCaptor<LocalAdminBuilding> bindingCaptor = ArgumentCaptor.forClass(LocalAdminBuilding.class);
        verify(localAdminBuildingRepository, times(2)).save(bindingCaptor.capture());
        assertThat(bindingCaptor.getAllValues())
                .extracting(b -> b.getBuildingId().id())
                .containsExactlyInAnyOrder("b2", "b3");
        bindingCaptor.getAllValues().forEach(b -> assertThat(b.getAssignedAt()).isEqualTo(FIXED_NOW));

        // Two outbox events emitted (one per new binding), both ASSIGNED.
        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, times(2)).save(eventCaptor.capture());
        var events = eventCaptor.getAllValues();
        assertThat(events).hasSize(2);
        for (OutboxEvent e : events) {
            assertThat(e.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
            LocalAdminBuildingEventDto dto = objectMapper.readValue(e.getPayload(), LocalAdminBuildingEventDto.class);
            assertThat(dto.eventId()).isEqualTo(e.getId());
            assertThat(dto.eventType()).isEqualTo("LOCAL_ADMIN_BUILDING_ASSIGNED");
            assertThat(dto.userId()).isEqualTo("u-1");
            assertThat(dto.assignedAt()).isEqualTo(FIXED_NOW);
            assertThat(dto.buildingId()).isIn("b2", "b3");
        }
        verify(localAdminBuildingRepository, never())
                .deleteByUserIdAndBuildingId(any(), any());
    }

    @Test
    void assignBuildings_throwsUserNotFoundException_whenUserNotFound() {
        UserId uid = new UserId("ghost");
        when(userRepository.findById(uid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignBuildings("ghost", List.of("b1")))
                .isInstanceOf(UserNotFoundException.class);

        verify(localAdminBuildingRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void assignBuildings_throwsIllegalArgumentException_whenInputsInvalid() {
        assertThatThrownBy(() -> service.assignBuildings("  ", List.of("b1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.assignBuildings("u-1", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.assignBuildings("u-1", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // revokeBuildings()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void revokeBuildings_deletesAndWritesOutboxEvents_forExistingBindingsOnly() throws Exception {
        UserId uid = new UserId("u-1");
        when(userRepository.findById(uid)).thenReturn(Optional.of(existingUser("u-1")));
        // b1 exists → deleted + outbox; b2 does not exist → idempotent skip.
        when(localAdminBuildingRepository.existsByUserIdAndBuildingId(uid, new BuildingId("b1")))
                .thenReturn(true);
        when(localAdminBuildingRepository.existsByUserIdAndBuildingId(uid, new BuildingId("b2")))
                .thenReturn(false);
        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.revokeBuildings("u-1", List.of("b1", "b2"));

        // Only b1 deleted.
        ArgumentCaptor<UserId> uidCaptor = ArgumentCaptor.forClass(UserId.class);
        ArgumentCaptor<BuildingId> bidCaptor = ArgumentCaptor.forClass(BuildingId.class);
        verify(localAdminBuildingRepository).deleteByUserIdAndBuildingId(uidCaptor.capture(), bidCaptor.capture());
        assertThat(uidCaptor.getValue().value()).isEqualTo("u-1");
        assertThat(bidCaptor.getValue().id()).isEqualTo("b1");

        // One REVOKED outbox event for b1, with assignedAt null and eventId == outbox id.
        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, times(1)).save(eventCaptor.capture());
        OutboxEvent e = eventCaptor.getValue();
        assertThat(e.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        LocalAdminBuildingEventDto dto = objectMapper.readValue(e.getPayload(), LocalAdminBuildingEventDto.class);
        assertThat(dto.eventId()).isEqualTo(e.getId());
        assertThat(dto.eventType()).isEqualTo("LOCAL_ADMIN_BUILDING_REVOKED");
        assertThat(dto.userId()).isEqualTo("u-1");
        assertThat(dto.buildingId()).isEqualTo("b1");
        assertThat(dto.assignedAt()).isNull();

        verify(localAdminBuildingRepository, never()).save(any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // getBuildingsForUser()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void getBuildingsForUser_returnsBuildingIds() {
        UserId uid = new UserId("u-1");
        when(localAdminBuildingRepository.findByUserId(uid)).thenReturn(List.of(
                new LocalAdminBuilding(uid, new BuildingId("b1"), FIXED_NOW),
                new LocalAdminBuilding(uid, new BuildingId("b2"), FIXED_NOW)
        ));

        List<String> buildings = service.getBuildingsForUser("u-1");

        assertThat(buildings).containsExactlyInAnyOrder("b1", "b2");
    }
}