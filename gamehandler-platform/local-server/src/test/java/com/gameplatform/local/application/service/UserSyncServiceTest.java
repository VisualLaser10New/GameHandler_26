package com.gameplatform.local.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.domain.ports.out.AdminRequestRepository;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.UserSyncAckDto;
import com.gameplatform.shared.dto.UserSyncDto;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserSyncServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-06T00:00:00Z");

    @Mock UserRepository userRepository;
    @Mock AdminRequestRepository adminRequestRepository;

    Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));

    private UserSyncService service;

    @BeforeEach
    void setUp() {
        service = new UserSyncService(userRepository, adminRequestRepository, clock);
    }

    @Test
    void shouldSyncUsers() {
        when(userRepository.findById(any(UserId.class))).thenReturn(Optional.empty());
        List<UserSyncDto> dtos = List.of(
                new UserSyncDto("u-1", "alice", "hash1", List.of("PLAYER")),
                new UserSyncDto("u-2", "bob", "hash2", List.of("OPERATOR")));

        service.syncUsers(dtos);

        verify(userRepository, times(2)).save(any(User.class));
    }

    @Test
    void shouldDoNothingWhenNullList() {
        service.syncUsers(null);
        verify(userRepository, never()).save(any());
        verify(userRepository, never()).findById(any(UserId.class));
    }

    @Test
    void shouldDoNothingWhenEmptyList() {
        service.syncUsers(List.of());
        verify(userRepository, never()).save(any());
        verify(userRepository, never()).findById(any(UserId.class));
    }

    @Test
    void shouldReturnValidationErrorAckForPoisonUserAndNotAbort() {
        when(userRepository.findById(any(UserId.class))).thenReturn(Optional.empty());
        UserSyncDto bad = new UserSyncDto("u-1", "", "hash", List.of("PLAYER"));
        // M3: poison user is caught inside the loop — batch is NOT aborted, no save, poison ack returned.
        List<UserSyncAckDto> acks = service.syncUsers(List.of(bad));

        verify(userRepository, never()).save(any());
        assertThat(acks).hasSize(1);
        assertThat(acks.get(0).applied()).isFalse();
        assertThat(acks.get(0).reason()).startsWith("VALIDATION_ERROR");
    }
}
