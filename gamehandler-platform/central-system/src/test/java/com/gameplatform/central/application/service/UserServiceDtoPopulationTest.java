package com.gameplatform.central.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gameplatform.central.domain.model.OutboxEvent;
import com.gameplatform.central.domain.model.User;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.central.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.UserSyncDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R4: verifies that {@link UserService} now populates {@code email} and {@code occurredAt} on the
 * {@link UserSyncDto} embedded in outbox events, and that the {@code occurredAt} for a
 * {@code USER_UPDATED} event is STRICTLY GREATER than the one emitted for the preceding
 * {@code USER_REGISTERED} event (time advances through the injected {@link Clock}).
 */
@ExtendWith(MockitoExtension.class)
class UserServiceDtoPopulationTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void occurredAtAdvancesBetweenRegisteredAndUpdated_andEmailIsPopulated() throws Exception {
        Instant registeredAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant updatedAt = registeredAt.plusSeconds(10);

        Clock stubClock = org.mockito.Mockito.mock(Clock.class);
        when(stubClock.instant()).thenReturn(registeredAt, updatedAt);

        UserService userService = new UserService(
                userRepository, outboxEventRepository, objectMapper, stubClock);

        when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User registered = userService.register("alice", "s3cr3t", "alice@example.com");

        when(userRepository.findById(registered.getId())).thenReturn(Optional.of(registered));

        userService.updateUser(registered.getId(), null, List.of("ADMIN"));

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, times(2)).save(eventCaptor.capture());

        OutboxEvent registeredEvent = eventCaptor.getAllValues().get(0);
        OutboxEvent updatedEvent = eventCaptor.getAllValues().get(1);

        assertThat(registeredEvent.getEventType()).isEqualTo("USER_REGISTERED");
        assertThat(updatedEvent.getEventType()).isEqualTo("USER_UPDATED");

        UserSyncDto registeredDto = objectMapper.readValue(registeredEvent.getPayload(), UserSyncDto.class);
        UserSyncDto updatedDto = objectMapper.readValue(updatedEvent.getPayload(), UserSyncDto.class);

        assertThat(registeredDto.occurredAt()).isEqualTo(registeredAt);
        assertThat(updatedDto.occurredAt()).isEqualTo(updatedAt);
        assertThat(updatedDto.occurredAt())
                .as("USER_UPDATED occurredAt must be strictly greater than USER_REGISTERED occurredAt")
                .isAfter(registeredDto.occurredAt());

        assertThat(registeredDto.email()).isEqualTo(registered.getEmail());
        assertThat(updatedDto.email())
                .as("email in the update payload must equal the saved user's email")
                .isEqualTo(registered.getEmail());
    }
}