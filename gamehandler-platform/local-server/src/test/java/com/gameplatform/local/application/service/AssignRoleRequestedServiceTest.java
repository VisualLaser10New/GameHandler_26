package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.AdminRequestDto;
import com.gameplatform.shared.dto.RoleAssignmentRequestedEventDto;
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
class AssignRoleRequestedServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-12T10:00:00Z");

    private User platformAdmin() {
        return new User(new UserId("u-1"), "alice", "hash", "alice@example.com",
                List.of("PLATFORM_ADMIN"), NOW, NOW);
    }

    @Mock UserRepository userRepository;
    @Mock AdminRequestOutboxWriter outboxWriter;

    private final Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));
    private AssignRoleRequestedService service;

    @BeforeEach
    void setUp() {
        service = new AssignRoleRequestedService(userRepository, outboxWriter, clock);
    }

    @Test
    void assign_writesPendingRequestWhenPlatformAdminHasRole() {
        when(userRepository.findById(any())).thenReturn(Optional.of(platformAdmin()));
        when(outboxWriter.writePendingRequest(any(), any(), any(), any(), any()))
                .thenReturn(new AdminRequestDto("req-1", "ROLE_ASSIGNMENT_REQUESTED",
                        "u-1", "PLATFORM_ADMIN", "building-1", "{}", "PENDING", null, NOW, null, "req-1"));

        AdminRequestDto result = service.assign("target-u", List.of("PLAYER", "OPERATOR"),
                "u-1", "PLATFORM_ADMIN", "building-1");

        assertEquals("PENDING", result.status());
        verify(outboxWriter).writePendingRequest(
                eq("ROLE_ASSIGNMENT_REQUESTED"), eq("u-1"), eq("PLATFORM_ADMIN"), eq("building-1"),
                argThat(p -> p instanceof RoleAssignmentRequestedEventDto
                        && "target-u".equals(((RoleAssignmentRequestedEventDto) p).targetUserId())
                        && List.of("PLAYER", "OPERATOR").equals(((RoleAssignmentRequestedEventDto) p).roles())));
    }

    @Test
    void assign_throwsAccessDeniedWhenLacksRole() {
        User non = new User(new UserId("u-1"), "alice", "hash", "alice@example.com",
                List.of("PLAYER"), NOW, NOW);
        when(userRepository.findById(any())).thenReturn(Optional.of(non));

        assertThrows(AccessDeniedException.class, () ->
                service.assign("u-2", List.of("PLAYER"), "u-1", "PLATFORM_ADMIN", "building-1"));
        verifyNoInteractions(outboxWriter);
    }

    @Test
    void assign_throwsIllegalArgumentWhenTargetUserIdBlank() {
        when(userRepository.findById(any())).thenReturn(Optional.of(platformAdmin()));
        assertThrows(IllegalArgumentException.class, () ->
                service.assign(" ", List.of("PLAYER"), "u-1", "PLATFORM_ADMIN", "building-1"));
    }

    @Test
    void assign_throwsIllegalArgumentWhenRolesNullOrEmpty() {
        when(userRepository.findById(any())).thenReturn(Optional.of(platformAdmin()));
        assertThrows(IllegalArgumentException.class, () ->
                service.assign("u-2", null, "u-1", "PLATFORM_ADMIN", "building-1"));
        assertThrows(IllegalArgumentException.class, () ->
                service.assign("u-2", List.of(), "u-1", "PLATFORM_ADMIN", "building-1"));
    }

    @Test
    void assign_acceptsMultipleRoles() {
        when(userRepository.findById(any())).thenReturn(Optional.of(platformAdmin()));
        when(outboxWriter.writePendingRequest(any(), any(), any(), any(), any()))
                .thenReturn(new AdminRequestDto("req-1", "ROLE_ASSIGNMENT_REQUESTED",
                        "u-1", "PLATFORM_ADMIN", "building-1", "{}", "PENDING", null, NOW, null, "req-1"));

        AdminRequestDto result = service.assign("u-2", List.of("PLAYER", "OPERATOR", "GAME_ADMIN"),
                "u-1", "PLATFORM_ADMIN", "building-1");
        assertEquals("PENDING", result.status());
    }
}
