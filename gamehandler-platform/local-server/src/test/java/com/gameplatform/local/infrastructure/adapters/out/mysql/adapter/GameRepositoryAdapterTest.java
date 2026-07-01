package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.GameJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.GameMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.GameJpaRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.domain.model.GameType;

@ExtendWith(MockitoExtension.class)
class GameRepositoryAdapterTest {

    @Mock
    private GameJpaRepository jpaRepository;
    @Mock
    private GameMapper mapper;
    @InjectMocks
    private GameRepositoryAdapter adapter;

    private Game sample() {
        return new Game(new GameId("g-1"), GameType.CHESS, "Chess", new BuildingId("b-1"), GameMachineStatus.AVAILABLE);
    }

    @Test
    void saveDelegates() {
        GameJpaEntity entity = new GameJpaEntity();
        GameJpaEntity saved = new GameJpaEntity();
        Game domain = sample();
        when(mapper.toEntity(domain)).thenReturn(entity);
        when(jpaRepository.save(entity)).thenReturn(saved);
        when(mapper.toDomain(saved)).thenReturn(domain);

        assertThat(adapter.save(domain)).isSameAs(domain);
        verify(jpaRepository).save(entity);
    }

    @Test
    void findByIdUsesIdAccessor() {
        GameJpaEntity e = new GameJpaEntity();
        when(jpaRepository.findById("g-1")).thenReturn(Optional.of(e));
        when(mapper.toDomain(e)).thenReturn(sample());
        assertThat(adapter.findById(new GameId("g-1"))).isPresent();
    }

    @Test
    void findByBuildingIdDelegates() {
        when(jpaRepository.findByBuildingId("b-1")).thenReturn(List.of());
        adapter.findByBuildingId(new BuildingId("b-1"));
        verify(jpaRepository).findByBuildingId("b-1");
    }

    @Test
    void findByStatusPassesEnumName() {
        when(jpaRepository.findByStatus(GameMachineStatus.AVAILABLE)).thenReturn(List.of());
        adapter.findByStatus(GameMachineStatus.AVAILABLE);
        verify(jpaRepository).findByStatus(GameMachineStatus.AVAILABLE);
    }

    @Test
    void findAllDelegates() {
        GameJpaEntity e = new GameJpaEntity();
        when(jpaRepository.findAll()).thenReturn(List.of(e));
        when(mapper.toDomain(e)).thenReturn(sample());
        assertThat(adapter.findAll()).hasSize(1);
    }
}
