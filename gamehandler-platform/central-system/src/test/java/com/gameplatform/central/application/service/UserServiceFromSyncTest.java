package com.gameplatform.central.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.model.User;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.central.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.UserRegisteredEventDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class UserServiceFromSyncTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, outboxEventRepository, new ObjectMapper(), Clock.systemUTC());
    }

    @Test
    void registerFromSync_shouldCreateUser_whenNoConflicts() {
        UserRegisteredEventDto dto = new UserRegisteredEventDto(
                "user-123", "alice", "alice@example.com", "hashed_pw", List.of("USER"), Instant.now()
        );
        when(userRepository.findById(new UserId("user-123"))).thenReturn(Optional.empty());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.registerFromSync(dto);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getId().value()).isEqualTo("user-123");
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed_pw");
        assertThat(saved.getRoles()).containsExactly("USER");

        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void registerFromSync_shouldSkip_whenUserIdAlreadyExists() {
        UserRegisteredEventDto dto = new UserRegisteredEventDto(
                "user-123", "alice", "alice@example.com", "hashed_pw", List.of("USER"), Instant.now()
        );
        when(userRepository.findById(new UserId("user-123")))
                .thenReturn(Optional.of(mock(User.class)));

        userService.registerFromSync(dto);

        verify(userRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void registerFromSync_shouldSkip_whenUsernameAlreadyExists() {
        UserRegisteredEventDto dto = new UserRegisteredEventDto(
                "user-123", "alice", "alice@example.com", "hashed_pw", List.of("USER"), Instant.now()
        );
        when(userRepository.findById(new UserId("user-123"))).thenReturn(Optional.empty());
        when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(mock(User.class)));

        userService.registerFromSync(dto);

        verify(userRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void registerFromSync_shouldSkip_whenEmailAlreadyExists() {
        UserRegisteredEventDto dto = new UserRegisteredEventDto(
                "user-123", "alice", "alice@example.com", "hashed_pw", List.of("USER"), Instant.now()
        );
        when(userRepository.findById(new UserId("user-123"))).thenReturn(Optional.empty());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(mock(User.class)));

        userService.registerFromSync(dto);

        verify(userRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    /**
     * C-R6 Option 1: when two buildings register the same username and the second loses
     * the DB unique-constraint race, {@code registerFromSync} must (a) NOT rethrow, (b) keep
     * the existing (winning) user's hashedPassword unchanged, and (c) emit a loud structured
     * WARN captured via a Logback {@link ListAppender} (LogCaptor is forbidden on this project).
     */
    @Test
    void registerFromSync_keepsFirstPasswordWhenTwoBuildingsRegisterSameUsername() {
        String winningHash = "$2a$10$winningBuildingHashXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX";
        String losingHash = "$2a$10$losingBuildingHashXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX";

        UserId existingId = new UserId("building-a-user");
        User existing = new User(
                existingId, "shared-username", winningHash, "a@example.com",
                List.of("USER"), Instant.parse("2026-01-01T00:00:00Z"));

        UserRegisteredEventDto losingDto = new UserRegisteredEventDto(
                "building-b-user", "shared-username", "b@example.com",
                losingHash, List.of("USER"), Instant.parse("2026-01-01T00:00:01Z"));

        when(userRepository.findById(new UserId("building-b-user"))).thenReturn(Optional.empty());
        when(userRepository.findByUsername("shared-username")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("b@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry for username"));

        Logger logbackLogger = (Logger) LoggerFactory.getLogger(UserService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);
        try {
            assertThatCode(() -> userService.registerFromSync(losingDto))
                    .doesNotThrowAnyException();

            verify(userRepository).save(any(User.class));

            assertThat(existing.getPasswordHash())
                    .as("existing winning user's password must remain unchanged")
                    .isEqualTo(winningHash);

            boolean loudWarnCaptured = appender.list.stream().anyMatch(e ->
                    e.getLevel() == Level.WARN
                            && e.getFormattedMessage().contains("Central user already exists")
                            && e.getFormattedMessage().contains("userId=building-b-user")
                            && e.getFormattedMessage().contains("username=shared-username")
                            && e.getFormattedMessage().contains("keeping existing password"));
            assertThat(loudWarnCaptured)
                    .as("expected a loud structured WARN for the central user collision")
                    .isTrue();
        } finally {
            logbackLogger.detachAppender(appender);
            appender.stop();
        }
    }
}
