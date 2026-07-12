package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gameplatform.local.application.service.AdminRequestOutboxWriter;
import com.gameplatform.local.application.service.RegisterTournamentParticipantRequestedService;
import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.AdminRequestDto;
import com.gameplatform.shared.dto.ParticipantRegisterRequestedEventDto;
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
class RegisterTournamentParticipantRequestedServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-12T10:00:00Z");

    private User player() {
        return new User(new UserId("u-1"), "alice", "hash", "alice@example.com",
                List.of("PLAYER"), NOW, NOW);
    }

    @Mock UserRepository userRepository;
    @Mock AdminRequestOutboxWriter outboxWriter;

    private final Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));
    private RegisterTournamentParticipantRequestedService service;

    @BeforeEach
    void setUp() {
        service = new RegisterTournamentParticipantRequestedService(userRepository, outboxWriter, clock);
    }

    @Test
    void register_writesPendingRequestWhenPlayerHasRole() {
        when(userRepository.findById(any())).thenReturn(Optional.of(player()));
        when(outboxWriter.writePendingRequest(any(), any(), any(), any(), any()))
                .thenReturn(new AdminRequestDto("req-1", "PARTICIPANT_REGISTER_REQUESTED",
                        "u-1", "PLAYER", "building-1", "{}", "PENDING", null, NOW, null, "req-1"));

        AdminRequestDto result = service.register("t-1", "u-1", "PLAYER", "building-1",
                "Team Alpha", List.of("u-2", "u-3"));

        assertEquals("PENDING", result.status());
        verify(outboxWriter).writePendingRequest(
                eq("PARTICIPANT_REGISTER_REQUESTED"), eq("u-1"), eq("PLAYER"), eq("building-1"),
                argThat(p -> p instanceof ParticipantRegisterRequestedEventDto
                        && "t-1".equals(((ParticipantRegisterRequestedEventDto) p).tournamentId())
                        && "Team Alpha".equals(((ParticipantRegisterRequestedEventDto) p).teamName())
                        && List.of("u-2", "u-3").equals(((ParticipantRegisterRequestedEventDto) p).teamMemberIds())));
    }

    @Test
    void register_throwsAccessDeniedWhenUserLacksPlayerRole() {
        User nonPlayer = new User(new UserId("u-1"), "alice", "hash", "alice@example.com",
                List.of("SPECTATOR"), NOW, NOW);
        when(userRepository.findById(any())).thenReturn(Optional.of(nonPlayer));

        assertThrows(AccessDeniedException.class, () ->
                service.register("t-1", "u-1", "PLAYER", "building-1", null, null));
        verifyNoInteractions(outboxWriter);
    }

    @Test
    void register_throwsIllegalArgumentWhenUserNotReplicated() {
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                service.register("t-1", "u-1", "PLAYER", "building-1", null, null));
        verifyNoInteractions(outboxWriter);
    }

    @Test
    void register_throwsIllegalArgumentWhenTournamentIdBlank() {
        when(userRepository.findById(any())).thenReturn(Optional.of(player()));

        assertThrows(IllegalArgumentException.class, () ->
                service.register(" ", "u-1", "PLAYER", "building-1", null, null));
        verifyNoInteractions(outboxWriter);
    }

    @Test
    void register_acceptsNullTeamFieldsForIndividualRegistration() {
        when(userRepository.findById(any())).thenReturn(Optional.of(player()));
        when(outboxWriter.writePendingRequest(any(), any(), any(), any(), any()))
                .thenReturn(new AdminRequestDto("req-1", "PARTICIPANT_REGISTER_REQUESTED",
                        "u-1", "PLAYER", "building-1", "{}", "PENDING", null, NOW, null, "req-1"));

        AdminRequestDto result = service.register("t-1", "u-1", "PLAYER", "building-1", null, null);
        assertEquals("PENDING", result.status());
        verify(outboxWriter).writePendingRequest(
                eq("PARTICIPANT_REGISTER_REQUESTED"), eq("u-1"), eq("PLAYER"), eq("building-1"),
                argThat(p -> p instanceof ParticipantRegisterRequestedEventDto
                        && ((ParticipantRegisterRequestedEventDto) p).teamName() == null
                        && ((ParticipantRegisterRequestedEventDto) p).teamMemberIds() == null));
    }
}
