package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gameplatform.local.domain.model.TournamentStandingLocal;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TournamentStandingLocalJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.TournamentStandingLocalMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.TournamentStandingLocalJpaRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class TournamentStandingLocalRepoAdapterTest {

    private static final Instant NOW = Instant.parse("2026-07-12T10:00:00Z");

    @Mock TournamentStandingLocalJpaRepository jpaRepository;
    @Mock TournamentStandingLocalMapper mapper;

    private TournamentStandingLocalRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TournamentStandingLocalRepositoryAdapter(jpaRepository, mapper);
    }

    private TournamentStandingLocal domain() {
        return new TournamentStandingLocal(new TournamentId("t-1"), "p-a", "Alice", 3, 1, 9, 1, NOW);
    }

    private TournamentStandingLocalJpaEntity entity() {
        return new TournamentStandingLocalJpaEntity("t-1", "p-a", "Alice", 3, 1, 9, 1, NOW);
    }

    @Test
    void save_delegatesToJpaRepositoryAndMapsBack() {
        TournamentStandingLocal d = domain();
        TournamentStandingLocalJpaEntity e = entity();
        when(mapper.toEntity(d)).thenReturn(e);
        when(jpaRepository.save(e)).thenReturn(e);
        when(mapper.toDomain(e)).thenReturn(d);

        TournamentStandingLocal result = adapter.save(d);

        assertSame(d, result);
        verify(mapper).toEntity(d);
        verify(jpaRepository).save(e);
        verify(mapper).toDomain(e);
    }

    @Test
    void save_nullReturnsNull() {
        assertNull(adapter.save(null));
        verifyNoInteractions(jpaRepository, mapper);
    }

    @Test
    void findByTournament_delegatesAndMaps() {
        TournamentStandingLocalJpaEntity e = entity();
        TournamentStandingLocal d = domain();
        when(jpaRepository.findByTournamentId("t-1")).thenReturn(List.of(e));
        when(mapper.toDomain(e)).thenReturn(d);

        List<TournamentStandingLocal> result = adapter.findByTournament(new TournamentId("t-1"));

        assertEquals(1, result.size());
        assertSame(d, result.get(0));
    }

    @Test
    void findByTournament_nullReturnsEmptyList() {
        assertTrue(adapter.findByTournament(null).isEmpty());
        verifyNoInteractions(jpaRepository);
    }

    @Test
    void deleteByTournament_delegates() {
        adapter.deleteByTournament(new TournamentId("t-1"));
        verify(jpaRepository).deleteByTournamentId("t-1");
    }

    @Test
    void deleteByTournament_nullIsNoOp() {
        adapter.deleteByTournament(null);
        verifyNoInteractions(jpaRepository);
    }

    @Test
    void existsByTournamentAndParticipantId_delegates() {
        when(jpaRepository.existsByTournamentIdAndParticipantId("t-1", "p-a")).thenReturn(true);
        assertTrue(adapter.existsByTournamentAndParticipantId(new TournamentId("t-1"), "p-a"));
    }

    @Test
    void existsByTournamentAndParticipantId_nullArgsReturnFalse() {
        assertFalse(adapter.existsByTournamentAndParticipantId(null, "p-a"));
        assertFalse(adapter.existsByTournamentAndParticipantId(new TournamentId("t-1"), null));
        assertFalse(adapter.existsByTournamentAndParticipantId(new TournamentId("t-1"), " "));
        verifyNoInteractions(jpaRepository);
    }
}
