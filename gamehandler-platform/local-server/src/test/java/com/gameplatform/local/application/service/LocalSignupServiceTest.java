package com.gameplatform.local.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gameplatform.local.domain.exception.UserAlreadyExistsException;
import com.gameplatform.local.domain.model.LocalSignupUser;
import com.gameplatform.local.domain.model.OutboxEvent;
import com.gameplatform.local.domain.ports.out.LocalSignupUserRepository;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.UserRegisteredEventDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

@ExtendWith(MockitoExtension.class)
class LocalSignupServiceTest {

    @Mock
    private LocalSignupUserRepository localSignupUserRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-25T10:00:00Z"), ZoneId.of("UTC"));

    private LocalSignupService service;

    @BeforeEach
    void setUp() {
        service = new LocalSignupService(
                localSignupUserRepository,
                outboxEventRepository,
                objectMapper,
                clock
        );
    }

    @Test
    void shouldRegisterUserAndCreateOutboxEvent() throws Exception {
        when(localSignupUserRepository.existsByUsername("alice")).thenReturn(false);
        when(localSignupUserRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(localSignupUserRepository.save(any(LocalSignupUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocalSignupUser result = service.register("alice", "password", "alice@example.com");

        assertThat(result.getUsername()).isEqualTo("alice");
        assertThat(result.getEmail()).isEqualTo("alice@example.com");
        assertThat(result.getUserId()).isNotNull();
        assertThat(result.getCreatedAt()).isEqualTo(Instant.parse("2026-06-25T10:00:00Z"));
        assertThat(result.getRoles()).containsExactly("PLAYER");
        assertThat(BCrypt.checkpw("password", result.getPasswordHash())).isTrue();

        ArgumentCaptor<LocalSignupUser> userCaptor = ArgumentCaptor.forClass(LocalSignupUser.class);
        verify(localSignupUserRepository).save(userCaptor.capture());
        LocalSignupUser savedUser = userCaptor.getValue();
        assertThat(savedUser.getUsername()).isEqualTo("alice");

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());
        OutboxEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("USER_REGISTERED");
        assertThat(event.getStatus()).isEqualTo("PENDING");
        assertThat(event.getPayload()).isNotBlank();

        UserRegisteredEventDto dto = objectMapper.readValue(event.getPayload(), UserRegisteredEventDto.class);
        assertThat(dto.username()).isEqualTo("alice");
        assertThat(dto.email()).isEqualTo("alice@example.com");
        assertThat(dto.userId()).isEqualTo(result.getUserId().value());
        assertThat(dto.roles()).containsExactly("PLAYER");
    }

    @Test
    void shouldRejectExistingUsername() {
        when(localSignupUserRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> service.register("alice", "password", "alice@example.com"))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("Username already exists");

        verify(localSignupUserRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void shouldRejectExistingEmail() {
        when(localSignupUserRepository.existsByUsername("alice")).thenReturn(false);
        when(localSignupUserRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register("alice", "password", "alice@example.com"))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("Email already exists");

        verify(localSignupUserRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void shouldRejectNullOrBlankInputs() {
        assertThatThrownBy(() -> service.register(null, "password", "alice@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.register("alice", null, "alice@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.register("alice", "password", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.register("  ", "password", "alice@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.register("alice", "  ", "alice@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.register("alice", "password", "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
