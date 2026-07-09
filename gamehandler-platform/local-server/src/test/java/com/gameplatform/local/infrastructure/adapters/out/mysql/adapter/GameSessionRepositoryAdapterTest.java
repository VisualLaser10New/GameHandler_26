package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.OutboxEventJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.OutboxEventJpaRepository;

import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.GameSessionJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.GameSessionMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.GameSessionJpaRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;

@ExtendWith(MockitoExtension.class)
class GameSessionRepositoryAdapterTest {

    @Mock
    private GameSessionJpaRepository jpaRepository;
    @Mock
    private GameSessionMapper mapper;
    @Mock
    private OutboxEventJpaRepository outboxEventRepository;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks
    private GameSessionRepositoryAdapter adapter;

    private GameSession sample() {
        return new GameSession(
            new GameSessionId("s-1"), new GameId("g-1"), GameType.CHESS,
            new BuildingId("b-1"), GameStatus.IN_PROGRESS,
            Instant.parse("2026-01-01T10:00:00Z"), null, null, null, null, null, List.of());
    }

    @Test
    void saveDelegates() {
        GameSessionJpaEntity entity = new GameSessionJpaEntity();
        GameSessionJpaEntity saved = new GameSessionJpaEntity();
        GameSession domain = sample();
        when(mapper.toEntity(domain)).thenReturn(entity);
        when(jpaRepository.saveAndFlush(entity)).thenReturn(saved);
        when(mapper.toDomain(saved)).thenReturn(domain);
        assertThat(adapter.save(domain)).isSameAs(domain);
    }

    @Test
    void findByIdUsesValueAccessor() {
        GameSessionJpaEntity e = new GameSessionJpaEntity();
        when(jpaRepository.findById("s-1")).thenReturn(Optional.of(e));
        when(mapper.toDomain(e)).thenReturn(sample());
        assertThat(adapter.findById(new GameSessionId("s-1"))).isPresent();
    }

    @Test
    void findByBuildingIdDelegates() {
        when(jpaRepository.findByBuildingId("b-1")).thenReturn(List.of());
        adapter.findByBuildingId(new BuildingId("b-1"));
        verify(jpaRepository).findByBuildingId("b-1");
    }

    @Test
    void findByGameTypePassesEnumName() {
        when(jpaRepository.findByGameType("CHESS")).thenReturn(List.of());
        adapter.findByGameType(GameType.CHESS);
        verify(jpaRepository).findByGameType("CHESS");
    }

    @Test
    void findByStatusPassesEnumName() {
        when(jpaRepository.findByStatus("IN_PROGRESS")).thenReturn(List.of());
        adapter.findByStatus(GameStatus.IN_PROGRESS);
        verify(jpaRepository).findByStatus("IN_PROGRESS");
    }

    @Test
    void findPendingSyncFiltersSyncedAndReturnsPending() {
        GameSessionJpaEntity session1 = new GameSessionJpaEntity();
        session1.setId("s-1");
        session1.setStatus("COMPLETED");

        GameSessionJpaEntity session2 = new GameSessionJpaEntity();
        session2.setId("s-2");
        session2.setStatus("ABORTED");

        when(jpaRepository.findByStatusIn(List.of("COMPLETED", "ABORTED")))
            .thenReturn(List.of(session1, session2));

        OutboxEventJpaEntity event = new OutboxEventJpaEntity();
        event.setEventType("GAME_SESSION_COMPLETED");
        event.setStatus("SENT");
        event.setPayload("{\"sessionId\":\"s-1\"}");

        when(outboxEventRepository.findByEventTypeAndStatus("GAME_SESSION_COMPLETED", "SENT"))
            .thenReturn(List.of(event));

        GameSession domainSession2 = mock(GameSession.class);
        when(mapper.toDomain(session2)).thenReturn(domainSession2);

        List<GameSession> pending = adapter.findPendingSync();

        assertThat(pending).containsExactly(domainSession2);
        verify(mapper, never()).toDomain(session1);
    }

    @Test
    void findActiveByGameIdPassesActiveStatuses() {
        when(jpaRepository.findFirstByGameIdAndStatusIn("g-1", List.of("WAITING", "IN_PROGRESS", "PAUSED")))
            .thenReturn(Optional.empty());
        assertThat(adapter.findActiveByGameId(new GameId("g-1"))).isEmpty();
        verify(jpaRepository).findFirstByGameIdAndStatusIn("g-1", List.of("WAITING", "IN_PROGRESS", "PAUSED"));
    }
}
