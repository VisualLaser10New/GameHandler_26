package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.AdminRequestDto;
import com.gameplatform.shared.dto.GameDefinitionUpsertRequestedEventDto;
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
import java.util.Map;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class UpsertGameDefinitionRequestedServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-12T10:00:00Z");

    private User gameAdmin() {
        return new User(new UserId("u-1"), "alice", "hash", "alice@example.com",
                List.of("GAME_ADMIN"), NOW, NOW);
    }

    @Mock UserRepository userRepository;
    @Mock AdminRequestOutboxWriter outboxWriter;

    private final Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));
    private UpsertGameDefinitionRequestedService service;

    @BeforeEach
    void setUp() {
        service = new UpsertGameDefinitionRequestedService(userRepository, outboxWriter, clock);
    }

    @Test
    void upsert_writesPendingRequestWhenGameAdminHasRole() {
        when(userRepository.findById(any())).thenReturn(Optional.of(gameAdmin()));
        when(outboxWriter.writePendingRequest(any(), any(), any(), any(), any()))
                .thenReturn(new AdminRequestDto("req-1", "GAME_DEFINITION_UPSERT_REQUESTED",
                        "u-1", "GAME_ADMIN", "building-1", "{}", "PENDING", null, NOW, null, "req-1"));

        AdminRequestDto result = service.upsert(GameType.CHESS, "Chess 2.0", 2, 2,
                false, Map.of("k", "v"), "u-1", "GAME_ADMIN", "building-1");

        assertEquals("PENDING", result.status());
        verify(outboxWriter).writePendingRequest(
                eq("GAME_DEFINITION_UPSERT_REQUESTED"), eq("u-1"), eq("GAME_ADMIN"), eq("building-1"),
                argThat(p -> p instanceof GameDefinitionUpsertRequestedEventDto
                        && GameType.CHESS == ((GameDefinitionUpsertRequestedEventDto) p).gameType()
                        && "Chess 2.0".equals(((GameDefinitionUpsertRequestedEventDto) p).name())));
    }

    @Test
    void upsert_throwsAccessDeniedWhenLacksRole() {
        User non = new User(new UserId("u-1"), "alice", "hash", "alice@example.com",
                List.of("PLAYER"), NOW, NOW);
        when(userRepository.findById(any())).thenReturn(Optional.of(non));

        assertThrows(AccessDeniedException.class, () ->
                service.upsert(GameType.CHESS, "Chess", 2, 2, false, null, "u-1", "GAME_ADMIN", "building-1"));
        verifyNoInteractions(outboxWriter);
    }

    @Test
    void upsert_throwsIllegalArgumentWhenGameTypeNull() {
        when(userRepository.findById(any())).thenReturn(Optional.of(gameAdmin()));
        assertThrows(IllegalArgumentException.class, () ->
                service.upsert(null, "x", 2, 2, false, null, "u-1", "GAME_ADMIN", "building-1"));
    }

    @Test
    void upsert_throwsIllegalArgumentWhenNameBlank() {
        when(userRepository.findById(any())).thenReturn(Optional.of(gameAdmin()));
        assertThrows(IllegalArgumentException.class, () ->
                service.upsert(GameType.CHESS, " ", 2, 2, false, null, "u-1", "GAME_ADMIN", "building-1"));
    }

    @Test
    void upsert_throwsIllegalArgumentWhenMinPlayersInvalid() {
        when(userRepository.findById(any())).thenReturn(Optional.of(gameAdmin()));
        assertThrows(IllegalArgumentException.class, () ->
                service.upsert(GameType.CHESS, "Chess", 0, 2, false, null, "u-1", "GAME_ADMIN", "building-1"));
    }

    @Test
    void upsert_acceptsRegistrationRulesNull() {
        when(userRepository.findById(any())).thenReturn(Optional.of(gameAdmin()));
        when(outboxWriter.writePendingRequest(any(), any(), any(), any(), any()))
                .thenReturn(new AdminRequestDto("req-1", "GAME_DEFINITION_UPSERT_REQUESTED",
                        "u-1", "GAME_ADMIN", "building-1", "{}", "PENDING", null, NOW, null, "req-1"));

        AdminRequestDto result = service.upsert(GameType.CHESS, "Chess", 2, 2, false, null, "u-1", "GAME_ADMIN", "building-1");
        assertEquals("PENDING", result.status());
    }
}
