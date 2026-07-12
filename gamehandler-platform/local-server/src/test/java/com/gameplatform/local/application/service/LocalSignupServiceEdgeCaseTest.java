package com.gameplatform.local.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gameplatform.local.domain.exception.UserAlreadyExistsException;
import com.gameplatform.local.domain.model.LocalSignupUser;
import com.gameplatform.local.domain.model.OutboxEvent;
import com.gameplatform.local.domain.ports.out.LocalSignupUserRepository;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCrypt;

@ExtendWith(MockitoExtension.class)
class LocalSignupServiceEdgeCaseTest {

    @Mock
    private LocalSignupUserRepository localSignupUserRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-25T10:00:00Z"), ZoneId.of("UTC"));

    private LocalSignupService service;

    @BeforeEach
    void setUp() {
        service = new LocalSignupService(localSignupUserRepository, outboxEventRepository, objectMapper, clock);
    }

    @Test
    @DisplayName("validates inputs before touching the repository")
    void shouldValidateInputsBeforeQueryingRepository() {
        assertThatThrownBy(() -> service.register(null, "pw", "alice@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.register("alice", null, "alice@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.register("alice", "pw", null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(localSignupUserRepository);
    }

    @Test
    @DisplayName("checks username existence before email existence and short-circuits")
    void shouldCheckUsernameBeforeEmail() {
        when(localSignupUserRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> service.register("alice", "pw", "alice@example.com"))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("Username");

        verify(localSignupUserRepository, never()).existsByEmail(any());
        verify(localSignupUserRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("each registration hashes the password with a random salt (identical plaintext -> different hashes)")
    void shouldProduceDifferentHashesForSamePassword() {
        when(localSignupUserRepository.existsByUsername(any())).thenReturn(false);
        when(localSignupUserRepository.existsByEmail(any())).thenReturn(false);
        when(localSignupUserRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LocalSignupUser a = service.register("alice", "samepass", "a@example.com");
        LocalSignupUser b = service.register("bob", "samepass", "b@example.com");

        assertThat(a.getPasswordHash()).isNotEqualTo(b.getPasswordHash());
        assertThat(BCrypt.checkpw("samepass", a.getPasswordHash())).isTrue();
        assertThat(BCrypt.checkpw("samepass", b.getPasswordHash())).isTrue();
        assertThat(BCrypt.checkpw("wrongpass", a.getPasswordHash())).isFalse();
    }

    @Test
    @DisplayName("generated userId is a valid UUID and createdAt comes from the injected clock")
    void shouldGenerateValidUuidAndUseClock() {
        when(localSignupUserRepository.existsByUsername(any())).thenReturn(false);
        when(localSignupUserRepository.existsByEmail(any())).thenReturn(false);
        when(localSignupUserRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LocalSignupUser user = service.register("alice", "pw", "a@example.com");

        assertThat(UUID.fromString(user.getUserId().value())).isNotNull();
        assertThat(user.getCreatedAt()).isEqualTo(Instant.parse("2026-06-25T10:00:00Z"));
        assertThat(user.getRoles()).containsExactly("PLAYER");
    }

    @Test
    @DisplayName("outbox event persistence failure propagates RuntimeException to enforce outbox atomicity")
    void shouldPropagateWhenOutboxSaveFails() {
        when(localSignupUserRepository.existsByUsername(any())).thenReturn(false);
        when(localSignupUserRepository.existsByEmail(any())).thenReturn(false);
        when(localSignupUserRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxEventRepository.save(any())).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> service.register("alice", "pw", "a@example.com"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("OutboxEvent");
    }

    @Test
    @DisplayName("BUG-L1 FIXED: concurrent unique-constraint violation on save is translated to UserAlreadyExistsException (409 instead of 500)")
    void shouldCatchDataIntegrityViolationOnConcurrentSave() {
        when(localSignupUserRepository.existsByUsername(any())).thenReturn(false);
        when(localSignupUserRepository.existsByEmail(any())).thenReturn(false);
        when(localSignupUserRepository.save(any())).thenThrow(new DataIntegrityViolationException("Duplicate entry"));

        assertThatThrownBy(() -> service.register("alice", "pw", "a@example.com"))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("EDGE-L5 FIXED: service trims username and email before processing")
    void shouldTrimUsername() {
        when(localSignupUserRepository.existsByUsername("alice")).thenReturn(false);
        when(localSignupUserRepository.existsByEmail("a@example.com")).thenReturn(false);
        when(localSignupUserRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LocalSignupUser user = service.register(" alice ", "pw", " a@example.com ");

        assertThat(user.getUsername()).isEqualTo("alice");
        assertThat(user.getEmail()).isEqualTo("a@example.com");
    }

    @Test
    @DisplayName("EDGE-L6 FIXED: service validates email format and rejects strings without proper email structure")
    void shouldRejectInvalidEmailFormat() {
        assertThatThrownBy(() -> service.register("alice", "pw", "not-an-email"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }

    @Test
    @DisplayName("EDGE-L7 FIXED: service rejects usernames exceeding the DB column length (100)")
    void shouldRejectOverlyLongUsername() {
        String longName = "a".repeat(101);
        assertThatThrownBy(() -> service.register(longName, "pw", "a@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username");
    }

    @Test
    @DisplayName("outbox event carries the hashed password (never the plaintext) and the same userId as the saved user")
    void shouldSerializeHashedPasswordAndUserIdInOutbox() throws Exception {
        when(localSignupUserRepository.existsByUsername(any())).thenReturn(false);
        when(localSignupUserRepository.existsByEmail(any())).thenReturn(false);
        when(localSignupUserRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LocalSignupUser user = service.register("alice", "secret", "a@example.com");

        org.mockito.ArgumentCaptor<OutboxEvent> captor = org.mockito.ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        com.gameplatform.shared.dto.UserRegisteredEventDto dto =
                objectMapper.readValue(captor.getValue().getPayload(),
                        com.gameplatform.shared.dto.UserRegisteredEventDto.class);
        assertThat(dto.hashedPassword()).isEqualTo(user.getPasswordHash());
        assertThat(dto.userId()).isEqualTo(user.getUserId().value());
        assertThat(dto.hashedPassword()).doesNotContain("secret");
    }
}
