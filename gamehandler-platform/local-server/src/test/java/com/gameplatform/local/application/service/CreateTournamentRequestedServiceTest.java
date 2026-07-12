package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.AdminRequestDto;
import com.gameplatform.shared.dto.TournamentCreateRequestedEventDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class CreateTournamentRequestedServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-12T10:00:00Z");
    private static final Instant STARTS_AT = Instant.parse("2026-08-01T10:00:00Z");

    private User platformAdmin() {
        return new User(new UserId("u-1"), "alice", "hash", "alice@example.com",
                List.of("PLATFORM_ADMIN"), NOW, NOW);
    }

    @Mock UserRepository userRepository;
    @Mock AdminRequestOutboxWriter outboxWriter;

    private final Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));
    private CreateTournamentRequestedService service;

    @BeforeEach
    void setUp() {
        service = new CreateTournamentRequestedService(userRepository, outboxWriter, clock);
    }

    @Test
    void create_writesPendingRequestWhenPlatformAdminHasRole() {
        when(userRepository.findById(any())).thenReturn(Optional.of(platformAdmin()));
        when(outboxWriter.writePendingRequest(any(), any(), any(), any(), any()))
                .thenReturn(new AdminRequestDto("req-1", "TOURNAMENT_CREATE_REQUESTED",
                        "u-1", "PLATFORM_ADMIN", "building-1", "{}", "PENDING", null, NOW, null, "req-1"));

        AdminRequestDto result = service.create("Cup", GameType.CHESS, false, 1, STARTS_AT,
                List.of("b-1", "b-2"), "u-1", "PLATFORM_ADMIN", "building-1");

        assertEquals("PENDING", result.status());
        verify(outboxWriter).writePendingRequest(
                eq("TOURNAMENT_CREATE_REQUESTED"), eq("u-1"), eq("PLATFORM_ADMIN"), eq("building-1"),
                argThat(p -> p instanceof TournamentCreateRequestedEventDto
                        && "Cup".equals(((TournamentCreateRequestedEventDto) p).name())
                        && List.of("b-1", "b-2").equals(((TournamentCreateRequestedEventDto) p).buildingIds())));
    }

    @Test
    void create_throwsAccessDeniedWhenLacksRole() {
        User non = new User(new UserId("u-1"), "alice", "hash", "alice@example.com",
                List.of("PLAYER"), NOW, NOW);
        when(userRepository.findById(any())).thenReturn(Optional.of(non));

        assertThrows(AccessDeniedException.class, () ->
                service.create("Cup", GameType.CHESS, false, 1, STARTS_AT, List.of("b-1", "b-2"),
                        "u-1", "PLATFORM_ADMIN", "building-1"));
        verifyNoInteractions(outboxWriter);
    }

    @Test
    void create_throwsIllegalArgumentWhenNameBlank() {
        when(userRepository.findById(any())).thenReturn(Optional.of(platformAdmin()));
        assertThrows(IllegalArgumentException.class, () ->
                service.create(" ", GameType.CHESS, false, 1, STARTS_AT, List.of("b-1", "b-2"),
                        "u-1", "PLATFORM_ADMIN", "building-1"));
    }

    @Test
    void create_throwsIllegalArgumentWhenGameTypeNull() {
        when(userRepository.findById(any())).thenReturn(Optional.of(platformAdmin()));
        assertThrows(IllegalArgumentException.class, () ->
                service.create("Cup", null, false, 1, STARTS_AT, List.of("b-1", "b-2"),
                        "u-1", "PLATFORM_ADMIN", "building-1"));
    }

    @Test
    void create_throwsIllegalArgumentWhenStartsAtNull() {
        when(userRepository.findById(any())).thenReturn(Optional.of(platformAdmin()));
        assertThrows(IllegalArgumentException.class, () ->
                service.create("Cup", GameType.CHESS, false, 1, null, List.of("b-1", "b-2"),
                        "u-1", "PLATFORM_ADMIN", "building-1"));
    }

    @Test
    void create_throwsIllegalArgumentWhenBuildingIdsHasSingleEntry() {
        when(userRepository.findById(any())).thenReturn(Optional.of(platformAdmin()));
        assertThrows(IllegalArgumentException.class, () ->
                service.create("Cup", GameType.CHESS, false, 1, STARTS_AT, List.of("b-1"),
                        "u-1", "PLATFORM_ADMIN", "building-1"));
    }

    @Test
    void create_throwsIllegalArgumentWhenBuildingIdsNull() {
        when(userRepository.findById(any())).thenReturn(Optional.of(platformAdmin()));
        assertThrows(IllegalArgumentException.class, () ->
                service.create("Cup", GameType.CHESS, false, 1, STARTS_AT, null,
                        "u-1", "PLATFORM_ADMIN", "building-1"));
    }
}
