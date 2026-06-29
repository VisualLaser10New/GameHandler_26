package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.gameplatform.local.domain.model.Reservation;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.ReservationJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.ReservationMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.ReservationJpaRepository;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.ReservationId;
import com.gameplatform.shared.domain.model.ReservationStatus;
import com.gameplatform.shared.domain.model.UserId;
import java.time.Instant;

@ExtendWith(MockitoExtension.class)
class ReservationRepositoryAdapterTest {

    @Mock
    private ReservationJpaRepository jpaRepository;
    @Mock
    private ReservationMapper mapper;

    @InjectMocks
    private ReservationRepositoryAdapter adapter;

    private Reservation sample() {
        return new Reservation(
            new ReservationId("r-1"), new GameId("g-1"), new UserId("u-1"),
            ReservationStatus.PENDING,
            Instant.parse("2026-01-01T10:00:00Z"),
            Instant.parse("2026-01-01T11:00:00Z"),
            Instant.parse("2026-01-01T09:00:00Z"));
    }

    @Test
    void saveDelegatesAndMapsBack() {
        Reservation domain = sample();
        ReservationJpaEntity entity = new ReservationJpaEntity();
        ReservationJpaEntity saved = new ReservationJpaEntity();
        when(mapper.toEntity(domain)).thenReturn(entity);
        when(jpaRepository.save(entity)).thenReturn(saved);
        when(mapper.toDomain(saved)).thenReturn(domain);

        Reservation result = adapter.save(domain);

        assertThat(result).isSameAs(domain);
        verify(jpaRepository).save(entity);
        verify(mapper).toDomain(saved);
    }

    @Test
    void findByIdMapsOptional() {
        ReservationJpaEntity entity = new ReservationJpaEntity();
        Reservation domain = sample();
        when(jpaRepository.findById("r-1")).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);

        Optional<Reservation> result = adapter.findById(new ReservationId("r-1"));

        assertThat(result).contains(domain);
    }

    @Test
    void findByIdMissingReturnsEmpty() {
        when(jpaRepository.findById("nope")).thenReturn(Optional.empty());
        assertThat(adapter.findById(new ReservationId("nope"))).isEmpty();
        verify(mapper, never()).toDomain(any());
    }

    @Test
    void findByUserIdDelegates() {
        ReservationJpaEntity e = new ReservationJpaEntity();
        when(jpaRepository.findByUserId("u-1")).thenReturn(List.of(e));
        when(mapper.toDomain(e)).thenReturn(sample());

        List<Reservation> out = adapter.findByUserId(new UserId("u-1"));

        assertThat(out).hasSize(1);
        verify(jpaRepository).findByUserId("u-1");
    }

    @Test
    void findByGameIdDelegates() {
        when(jpaRepository.findByGameId("g-1")).thenReturn(List.of());
        adapter.findByGameId(new GameId("g-1"));
        verify(jpaRepository).findByGameId("g-1");
    }

    @Test
    void findByStatusPassesEnumName() {
        when(jpaRepository.findByStatus("PENDING")).thenReturn(List.of());
        adapter.findByStatus(ReservationStatus.PENDING);
        verify(jpaRepository).findByStatus("PENDING");
    }

    @Test
    void findExpiredPassesPendingAndNow() {
        Instant now = Instant.now();
        when(jpaRepository.findByStatusInAndEndTimeBefore(List.of("PENDING"), now))
            .thenReturn(List.of());
        adapter.findExpired(now);
        verify(jpaRepository).findByStatusInAndEndTimeBefore(List.of("PENDING"), now);
    }
}
