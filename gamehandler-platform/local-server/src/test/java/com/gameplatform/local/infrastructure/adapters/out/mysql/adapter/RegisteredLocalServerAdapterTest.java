package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gameplatform.local.domain.model.RegisteredLocalServerLocal;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.RegisteredLocalServerLocalJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.RegisteredLocalServerLocalMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.RegisteredLocalServerLocalJpaRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class RegisteredLocalServerAdapterTest {

    private static final Instant NOW = Instant.parse("2026-07-12T10:00:00Z");

    @Mock RegisteredLocalServerLocalJpaRepository jpaRepository;
    @Mock RegisteredLocalServerLocalMapper mapper;

    private RegisteredLocalServerLocalRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RegisteredLocalServerLocalRepositoryAdapter(jpaRepository, mapper);
    }

    private RegisteredLocalServerLocal domain() {
        return new RegisteredLocalServerLocal(new BuildingId("building-1"), "https://x:8081", NOW, true, NOW);
    }

    private RegisteredLocalServerLocalJpaEntity entity() {
        return new RegisteredLocalServerLocalJpaEntity("building-1", "https://x:8081", NOW, true, NOW);
    }

    @Test
    void save_delegatesAndMapsBack() {
        RegisteredLocalServerLocal d = domain();
        RegisteredLocalServerLocalJpaEntity e = entity();
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
    void findById_delegatesAndMaps() {
        RegisteredLocalServerLocalJpaEntity e = entity();
        RegisteredLocalServerLocal d = domain();
        when(jpaRepository.findById("building-1")).thenReturn(Optional.of(e));
        when(mapper.toDomain(e)).thenReturn(d);

        assertTrue(adapter.findById("building-1").isPresent());
    }

    @Test
    void findById_nullOrBlankReturnsEmpty() {
        assertTrue(adapter.findById(null).isEmpty());
        assertTrue(adapter.findById(" ").isEmpty());
        verifyNoInteractions(jpaRepository);
    }

    @Test
    void findAll_maps() {
        RegisteredLocalServerLocalJpaEntity e = entity();
        RegisteredLocalServerLocal d = domain();
        when(jpaRepository.findAll()).thenReturn(List.of(e));
        when(mapper.toDomain(e)).thenReturn(d);

        List<RegisteredLocalServerLocal> result = adapter.findAll();
        assertEquals(1, result.size());
        assertSame(d, result.get(0));
    }

    @Test
    void deleteById_delegates() {
        adapter.deleteById("building-1");
        verify(jpaRepository).deleteById("building-1");
    }

    @Test
    void deleteById_nullOrBlankIsNoOp() {
        adapter.deleteById(null);
        adapter.deleteById(" ");
        verifyNoInteractions(jpaRepository);
    }
}
