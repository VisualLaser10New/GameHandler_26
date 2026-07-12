package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gameplatform.local.domain.model.TournamentParticipantLocal;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TournamentParticipantLocalJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.TournamentParticipantLocalMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.TournamentParticipantLocalJpaRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class TournamentParticipantLocalRepositoryAdapterTest {

    private static final Instant NOW = Instant.parse("2026-07-12T10:00:00Z");

    @Mock TournamentParticipantLocalJpaRepository jpaRepository;
    @Mock TournamentParticipantLocalMapper mapper;

    private TournamentParticipantLocalRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TournamentParticipantLocalRepositoryAdapter(jpaRepository, mapper);
    }

    private TournamentParticipantLocal domain() {
        return new TournamentParticipantLocal(new TournamentId("t-1"), "p-a", false, "Alice", NOW, NOW);
    }

    private TournamentParticipantLocalJpaEntity entity() {
        return new TournamentParticipantLocalJpaEntity("t-1", "p-a", false, "Alice", NOW, NOW);
    }

    @Test
    void save_delegatesAndMapsBack() {
        TournamentParticipantLocal d = domain();
        TournamentParticipantLocalJpaEntity e = entity();
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
    void findByTournament_delegatesAndMaps() {
        TournamentParticipantLocalJpaEntity e = entity();
        TournamentParticipantLocal d = domain();
        when(jpaRepository.findByTournamentId("t-1")).thenReturn(List.of(e));
        when(mapper.toDomain(e)).thenReturn(d);

        List<TournamentParticipantLocal> result = adapter.findByTournament(new TournamentId("t-1"));
        assertEquals(1, result.size());
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
    void deleteByTournamentAndParticipantId_delegates() {
        adapter.deleteByTournamentAndParticipantId(new TournamentId("t-1"), "p-a");
        verify(jpaRepository).deleteByTournamentIdAndParticipantId("t-1", "p-a");
    }

    @Test
    void deleteByTournamentAndParticipantId_nullOrBlankArgsAreNoOp() {
        adapter.deleteByTournamentAndParticipantId(null, "p-a");
        adapter.deleteByTournamentAndParticipantId(new TournamentId("t-1"), null);
        adapter.deleteByTournamentAndParticipantId(new TournamentId("t-1"), " ");
        verifyNoInteractions(jpaRepository);
    }
}
