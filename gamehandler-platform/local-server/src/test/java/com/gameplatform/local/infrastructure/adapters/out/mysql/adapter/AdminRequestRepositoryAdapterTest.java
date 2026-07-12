package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gameplatform.local.domain.model.AdminRequestLocal;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.AdminRequestLocalJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.AdminRequestLocalMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.AdminRequestLocalJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class AdminRequestRepositoryAdapterTest {

    private static final Instant NOW = Instant.parse("2026-07-12T12:00:00Z");

    @Mock AdminRequestLocalJpaRepository jpaRepository;
    @Mock AdminRequestLocalMapper mapper;

    private final Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));
    private AdminRequestRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AdminRequestRepositoryAdapter(jpaRepository, mapper, clock);
    }

    private AdminRequestLocal domain() {
        return new AdminRequestLocal("req-1", "TOURNAMENT_CREATE_REQUESTED", "u-1",
                "PLATFORM_ADMIN", "building-1", "{}", "PENDING", null, NOW, null, "req-1");
    }

    private AdminRequestLocalJpaEntity entity() {
        return new AdminRequestLocalJpaEntity("req-1", "TOURNAMENT_CREATE_REQUESTED", "u-1",
                "PLATFORM_ADMIN", "building-1", "{}", "PENDING", null, NOW, null, "req-1");
    }

    @Test
    void save_delegatesAndMaps() {
        AdminRequestLocal d = domain();
        AdminRequestLocalJpaEntity e = entity();
        when(mapper.toEntity(d)).thenReturn(e);
        when(jpaRepository.save(e)).thenReturn(e);
        when(mapper.toDomain(e)).thenReturn(d);

        assertSame(d, adapter.save(d));
        verify(jpaRepository).save(e);
    }

    @Test
    void save_nullReturnsNull() {
        assertNull(adapter.save(null));
        verifyNoInteractions(jpaRepository, mapper);
    }

    @Test
    void findByRequestId_delegatesAndMaps() {
        AdminRequestLocalJpaEntity e = entity();
        AdminRequestLocal d = domain();
        when(jpaRepository.findById("req-1")).thenReturn(Optional.of(e));
        when(mapper.toDomain(e)).thenReturn(d);

        assertTrue(adapter.findByRequestId("req-1").isPresent());
    }

    @Test
    void findByRequestId_nullOrBlankReturnsEmpty() {
        assertTrue(adapter.findByRequestId(null).isEmpty());
        assertTrue(adapter.findByRequestId(" ").isEmpty());
        verifyNoInteractions(jpaRepository);
    }

    @Test
    void findByActingUserId_delegatesAndMaps() {
        AdminRequestLocalJpaEntity e = entity();
        AdminRequestLocal d = domain();
        when(jpaRepository.findByActingUserId("u-1")).thenReturn(List.of(e));
        when(mapper.toDomain(e)).thenReturn(d);

        List<AdminRequestLocal> result = adapter.findByActingUserId("u-1");
        assertEquals(1, result.size());
    }

    @Test
    void findByActingUserId_nullOrBlankReturnsEmpty() {
        assertTrue(adapter.findByActingUserId(null).isEmpty());
        assertTrue(adapter.findByActingUserId(" ").isEmpty());
        verifyNoInteractions(jpaRepository);
    }

    @Test
    void findByActingUserIdAndStatus_delegatesAndMaps() {
        AdminRequestLocalJpaEntity e = entity();
        AdminRequestLocal d = domain();
        when(jpaRepository.findByActingUserIdAndStatus("u-1", "PENDING")).thenReturn(List.of(e));
        when(mapper.toDomain(e)).thenReturn(d);

        List<AdminRequestLocal> result = adapter.findByActingUserIdAndStatus("u-1", "PENDING");
        assertEquals(1, result.size());
    }

    @Test
    void findByActingUserIdAndStatus_nullOrBlankArgsReturnEmpty() {
        assertTrue(adapter.findByActingUserIdAndStatus(null, "PENDING").isEmpty());
        assertTrue(adapter.findByActingUserIdAndStatus("u-1", null).isEmpty());
        verifyNoInteractions(jpaRepository);
    }

    @Test
    void markCompleted_delegatesWithClockWhenNowNull() {
        when(jpaRepository.markCompleted(eq("req-1"), eq("{}"), eq(NOW))).thenReturn(1);

        int result = adapter.markCompleted("req-1", "{}", null);

        assertEquals(1, result);
        verify(jpaRepository).markCompleted("req-1", "{}", NOW);
    }

    @Test
    void markCompleted_usesExplicitNowWhenProvided() {
        Instant explicit = Instant.parse("2026-08-01T10:00:00Z");
        when(jpaRepository.markCompleted(eq("req-1"), eq("{}"), eq(explicit))).thenReturn(1);

        int result = adapter.markCompleted("req-1", "{}", explicit);

        assertEquals(1, result);
        verify(jpaRepository).markCompleted("req-1", "{}", explicit);
        // The clock's NOW must NOT be used when an explicit instant is passed.
        verify(jpaRepository, never()).markCompleted("req-1", "{}", NOW);
    }

    @Test
    void markCompleted_nullOrBlankReturnsZero() {
        assertEquals(0, adapter.markCompleted(null, "{}", NOW));
        assertEquals(0, adapter.markCompleted(" ", "{}", NOW));
        verifyNoInteractions(jpaRepository);
    }

    @Test
    void markFailed_delegatesWithClockWhenNowNull() {
        when(jpaRepository.markFailed(eq("req-1"), eq("{\"reason\":\"TIMEOUT\"}"), eq(NOW))).thenReturn(1);

        int result = adapter.markFailed("req-1", "{\"reason\":\"TIMEOUT\"}", null);

        assertEquals(1, result);
        verify(jpaRepository).markFailed("req-1", "{\"reason\":\"TIMEOUT\"}", NOW);
    }

    @Test
    void markFailed_usesExplicitNowWhenProvided() {
        Instant explicit = Instant.parse("2026-08-01T10:00:00Z");
        when(jpaRepository.markFailed(eq("req-1"), anyString(), eq(explicit))).thenReturn(1);

        adapter.markFailed("req-1", "{\"reason\":\"TIMEOUT\"}", explicit);

        verify(jpaRepository).markFailed("req-1", "{\"reason\":\"TIMEOUT\"}", explicit);
    }

    @Test
    void markFailed_nullOrBlankReturnsZero() {
        assertEquals(0, adapter.markFailed(null, "{}", NOW));
        assertEquals(0, adapter.markFailed(" ", "{}", NOW));
        verifyNoInteractions(jpaRepository);
    }

    @Test
    void findPendingOlderThan_delegatesAndMaps() {
        AdminRequestLocalJpaEntity e = entity();
        AdminRequestLocal d = domain();
        Instant threshold = NOW.minus(1800000L, java.time.temporal.ChronoUnit.MILLIS);
        when(jpaRepository.findByStatusAndCreatedAtBefore("PENDING", threshold)).thenReturn(List.of(e));
        when(mapper.toDomain(e)).thenReturn(d);

        List<AdminRequestLocal> result = adapter.findPendingOlderThan(threshold);
        assertEquals(1, result.size());
    }

    @Test
    void findPendingOlderThan_nullReturnsEmpty() {
        assertTrue(adapter.findPendingOlderThan(null).isEmpty());
        verifyNoInteractions(jpaRepository);
    }
}
