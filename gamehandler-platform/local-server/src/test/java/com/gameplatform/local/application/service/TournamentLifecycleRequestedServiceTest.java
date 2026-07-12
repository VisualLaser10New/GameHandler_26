package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.AdminRequestDto;
import com.gameplatform.shared.dto.TournamentLifecycleRequestedEventDto;
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
class TournamentLifecycleRequestedServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-12T10:00:00Z");

    private User platformAdmin() {
        return new User(new UserId("u-1"), "alice", "hash", "alice@example.com",
                List.of("PLATFORM_ADMIN"), NOW, NOW);
    }

    @Mock UserRepository userRepository;
    @Mock AdminRequestOutboxWriter outboxWriter;

    private final Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));
    private TournamentLifecycleRequestedService service;

    @BeforeEach
    void setUp() {
        service = new TournamentLifecycleRequestedService(userRepository, outboxWriter, clock);
    }

    @Test
    void lifecycle_open_writesPendingRequest() {
        when(userRepository.findById(any())).thenReturn(Optional.of(platformAdmin()));
        when(outboxWriter.writePendingRequest(any(), any(), any(), any(), any()))
                .thenReturn(new AdminRequestDto("req-1", "TOURNAMENT_OPEN_REQUESTED",
                        "u-1", "PLATFORM_ADMIN", "building-1", "{}", "PENDING", null, NOW, null, "req-1"));

        AdminRequestDto result = service.lifecycle("TOURNAMENT_OPEN_REQUESTED", "t-1",
                "u-1", "PLATFORM_ADMIN", "building-1");

        assertEquals("TOURNAMENT_OPEN_REQUESTED", result.eventType());
        verify(outboxWriter).writePendingRequest(
                eq("TOURNAMENT_OPEN_REQUESTED"), eq("u-1"), eq("PLATFORM_ADMIN"), eq("building-1"),
                argThat(p -> p instanceof TournamentLifecycleRequestedEventDto
                        && "t-1".equals(((TournamentLifecycleRequestedEventDto) p).tournamentId())));
    }

    @Test
    void lifecycle_cancel_writesPendingRequest() {
        when(userRepository.findById(any())).thenReturn(Optional.of(platformAdmin()));
        when(outboxWriter.writePendingRequest(any(), any(), any(), any(), any()))
                .thenReturn(new AdminRequestDto("req-1", "TOURNAMENT_CANCEL_REQUESTED",
                        "u-1", "PLATFORM_ADMIN", "building-1", "{}", "PENDING", null, NOW, null, "req-1"));

        AdminRequestDto result = service.lifecycle("TOURNAMENT_CANCEL_REQUESTED", "t-1",
                "u-1", "PLATFORM_ADMIN", "building-1");
        assertEquals("TOURNAMENT_CANCEL_REQUESTED", result.eventType());
    }

    @Test
    void lifecycle_schedule_writesPendingRequest() {
        when(userRepository.findById(any())).thenReturn(Optional.of(platformAdmin()));
        when(outboxWriter.writePendingRequest(any(), any(), any(), any(), any()))
                .thenReturn(new AdminRequestDto("req-1", "TOURNAMENT_SCHEDULE_REQUESTED",
                        "u-1", "PLATFORM_ADMIN", "building-1", "{}", "PENDING", null, NOW, null, "req-1"));

        AdminRequestDto result = service.lifecycle("TOURNAMENT_SCHEDULE_REQUESTED", "t-1",
                "u-1", "PLATFORM_ADMIN", "building-1");
        assertEquals("TOURNAMENT_SCHEDULE_REQUESTED", result.eventType());
    }

    @Test
    void lifecycle_throwsIllegalArgumentWhenEventTypeUnsupported() {
        assertThrows(IllegalArgumentException.class, () ->
                service.lifecycle("FOO", "t-1", "u-1", "PLATFORM_ADMIN", "building-1"));
        verifyNoInteractions(userRepository);
        verifyNoInteractions(outboxWriter);
    }

    @Test
    void lifecycle_throwsAccessDeniedWhenLacksRole() {
        User non = new User(new UserId("u-1"), "alice", "hash", "alice@example.com",
                List.of("PLAYER"), NOW, NOW);
        when(userRepository.findById(any())).thenReturn(Optional.of(non));

        assertThrows(AccessDeniedException.class, () ->
                service.lifecycle("TOURNAMENT_OPEN_REQUESTED", "t-1", "u-1", "PLATFORM_ADMIN", "building-1"));
        verifyNoInteractions(outboxWriter);
    }

    @Test
    void lifecycle_throwsIllegalArgumentWhenTournamentIdBlank() {
        when(userRepository.findById(any())).thenReturn(Optional.of(platformAdmin()));
        assertThrows(IllegalArgumentException.class, () ->
                service.lifecycle("TOURNAMENT_OPEN_REQUESTED", " ", "u-1", "PLATFORM_ADMIN", "building-1"));
        verifyNoInteractions(outboxWriter);
    }
}
