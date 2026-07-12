package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gameplatform.local.domain.model.TournamentSummaryLocal;
import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.domain.ports.out.TournamentSummaryLocalRepository;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.AdminRequestDto;
import com.gameplatform.shared.dto.TournamentDeleteRequestedEventDto;
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
class DeleteTournamentRequestedServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-12T10:00:00Z");
    private static final Instant STARTS_AT = Instant.parse("2026-08-01T10:00:00Z");

    private User platformAdmin() {
        return new User(new UserId("u-1"), "alice", "hash", "alice@example.com",
                List.of("PLATFORM_ADMIN"), NOW, NOW);
    }

    private TournamentSummaryLocal summary(TournamentStatus status) {
        return new TournamentSummaryLocal(new TournamentId("t-1"), "Cup", GameType.CHESS,
                false, 1, status, STARTS_AT, null, List.of("b-1", "b-2"), 0, false, NOW);
    }

    @Mock UserRepository userRepository;
    @Mock TournamentSummaryLocalRepository tournamentSummaryLocalRepository;
    @Mock AdminRequestOutboxWriter outboxWriter;

    private final Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));
    private DeleteTournamentRequestedService service;

    @BeforeEach
    void setUp() {
        service = new DeleteTournamentRequestedService(userRepository, tournamentSummaryLocalRepository, outboxWriter, clock);
    }

    @Test
    void delete_writesPendingRequestWhenTournamentIsDraft() {
        when(userRepository.findById(any())).thenReturn(Optional.of(platformAdmin()));
        when(tournamentSummaryLocalRepository.findById(new TournamentId("t-1"))).thenReturn(Optional.of(summary(TournamentStatus.DRAFT)));
        when(outboxWriter.writePendingRequest(any(), any(), any(), any(), any()))
                .thenReturn(new AdminRequestDto("req-1", "TOURNAMENT_DELETE_REQUESTED",
                        "u-1", "PLATFORM_ADMIN", "building-1", "{}", "PENDING", null, NOW, null, "req-1"));

        AdminRequestDto result = service.delete("t-1", "u-1", "PLATFORM_ADMIN", "building-1");

        assertEquals("PENDING", result.status());
        verify(outboxWriter).writePendingRequest(
                eq("TOURNAMENT_DELETE_REQUESTED"), eq("u-1"), eq("PLATFORM_ADMIN"), eq("building-1"),
                argThat(p -> p instanceof TournamentDeleteRequestedEventDto
                        && "t-1".equals(((TournamentDeleteRequestedEventDto) p).tournamentId())));
        verify(outboxWriter, never()).writeFailedRequest(any(), any(), any(), any(), any(), any());
    }

    @Test
    void delete_writesFailedRequestWhenTournamentNotDraft() {
        when(userRepository.findById(any())).thenReturn(Optional.of(platformAdmin()));
        when(tournamentSummaryLocalRepository.findById(new TournamentId("t-1")))
                .thenReturn(Optional.of(summary(TournamentStatus.OPEN_REGISTRATION)));
        when(outboxWriter.writeFailedRequest(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AdminRequestDto("req-1", "TOURNAMENT_DELETE_REQUESTED",
                        "u-1", "PLATFORM_ADMIN", "building-1", "{}", "FAILED",
                        "{\"reason\":\"NOT_DRAFT\",\"status\":\"OPEN_REGISTRATION\"}", NOW, NOW, null));

        AdminRequestDto result = service.delete("t-1", "u-1", "PLATFORM_ADMIN", "building-1");

        assertEquals("FAILED", result.status());
        verify(outboxWriter).writeFailedRequest(
                eq("TOURNAMENT_DELETE_REQUESTED"), eq("u-1"), eq("PLATFORM_ADMIN"), eq("building-1"),
                any(), argThat(s -> s.contains("NOT_DRAFT")));
        verify(outboxWriter, never()).writePendingRequest(any(), any(), any(), any(), any());
    }

    @Test
    void delete_writesFailedRequestWhenTournamentNotFound() {
        when(userRepository.findById(any())).thenReturn(Optional.of(platformAdmin()));
        when(tournamentSummaryLocalRepository.findById(new TournamentId("t-1"))).thenReturn(Optional.empty());
        when(outboxWriter.writeFailedRequest(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AdminRequestDto("req-1", "TOURNAMENT_DELETE_REQUESTED",
                        "u-1", "PLATFORM_ADMIN", "building-1", "{}", "FAILED",
                        "{\"reason\":\"NOT_FOUND\"}", NOW, NOW, null));

        AdminRequestDto result = service.delete("t-1", "u-1", "PLATFORM_ADMIN", "building-1");

        assertEquals("FAILED", result.status());
        verify(outboxWriter).writeFailedRequest(
                eq("TOURNAMENT_DELETE_REQUESTED"), eq("u-1"), eq("PLATFORM_ADMIN"), eq("building-1"),
                any(), argThat(s -> s.contains("NOT_FOUND")));
        verify(outboxWriter, never()).writePendingRequest(any(), any(), any(), any(), any());
    }

    @Test
    void delete_throwsAccessDeniedWhenLacksRole() {
        User non = new User(new UserId("u-1"), "alice", "hash", "alice@example.com",
                List.of("PLAYER"), NOW, NOW);
        when(userRepository.findById(any())).thenReturn(Optional.of(non));

        assertThrows(AccessDeniedException.class, () ->
                service.delete("t-1", "u-1", "PLATFORM_ADMIN", "building-1"));
        verifyNoInteractions(tournamentSummaryLocalRepository);
        verifyNoInteractions(outboxWriter);
    }

    @Test
    void delete_throwsIllegalArgumentWhenTournamentIdBlank() {
        when(userRepository.findById(any())).thenReturn(Optional.of(platformAdmin()));
        assertThrows(IllegalArgumentException.class, () ->
                service.delete(" ", "u-1", "PLATFORM_ADMIN", "building-1"));
    }
}