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
import org.mockito.junit.jupiter.MockitoExtension;

import com.gameplatform.local.domain.model.OutboxEvent;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.OutboxEventJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.OutboxEventMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.OutboxEventJpaRepository;

@ExtendWith(MockitoExtension.class)
class OutboxEventRepositoryAdapterTest {

    @Mock
    private OutboxEventJpaRepository jpaRepository;
    @Mock
    private OutboxEventMapper mapper;
    @InjectMocks
    private OutboxEventRepositoryAdapter adapter;

    private OutboxEvent sample() {
        return new OutboxEvent("e-1", "TYPE", "{}", "PENDING", Instant.now(), null, 0);
    }

    @Test
    void saveDelegates() {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
        OutboxEventJpaEntity saved = new OutboxEventJpaEntity();
        OutboxEvent domain = sample();
        when(mapper.toEntity(domain)).thenReturn(entity);
        when(jpaRepository.save(entity)).thenReturn(saved);
        when(mapper.toDomain(saved)).thenReturn(domain);
        assertThat(adapter.save(domain)).isSameAs(domain);
    }

    @Test
    void findPendingQueriesPendingStatus() {
        when(jpaRepository.findByStatusOrderByCreatedAtAsc("PENDING")).thenReturn(List.of());
        adapter.findPending();
        verify(jpaRepository).findByStatusOrderByCreatedAtAsc("PENDING");
    }

    @Test
    void markAsSentReadsMutatesAndSaves() {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
        OutboxEvent domain = sample();
        OutboxEventJpaEntity updated = new OutboxEventJpaEntity();
        when(jpaRepository.findById("e-1")).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);
        when(mapper.toEntity(domain)).thenReturn(updated);

        adapter.markAsSent("e-1");

        verify(jpaRepository).save(updated);
        assertThat(domain.getStatus()).isEqualTo("SENT"); // markAsSent impostato dal dominio
    }

    @Test
    void markAsSentMissingDoesNothing() {
        when(jpaRepository.findById("nope")).thenReturn(Optional.empty());
        adapter.markAsSent("nope");
        verify(jpaRepository, never()).save(any());
    }

    @Test
    void incrementRetryReadsMutatesAndSaves() {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
        OutboxEvent domain = sample();
        when(jpaRepository.findById("e-1")).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);
        when(mapper.toEntity(domain)).thenReturn(new OutboxEventJpaEntity());

        adapter.incrementRetry("e-1");

        assertThat(domain.getRetryCount()).isEqualTo(1);
    }
}
