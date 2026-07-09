package com.gameplatform.local.infrastructure.adapters.out.mysql.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.GameJpaEntity;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.domain.model.GameType;
import org.junit.jupiter.api.Test;

class GameMapperTest {

    private final GameMapper mapper = new GameMapper();

    @Test
    void toEntityAndBackIsSymmetric() {
        Game domain = new Game(
            new GameId("game-1"), GameType.CHESS, "Scacchi",
            new BuildingId("b-1"), GameMachineStatus.IN_USE);

        GameJpaEntity entity = mapper.toEntity(domain);

        assertThat(entity.getId()).isEqualTo("game-1");
        assertThat(entity.getGameType()).isEqualTo("CHESS");
        assertThat(entity.getName()).isEqualTo("Scacchi");
        assertThat(entity.getBuildingId()).isEqualTo("b-1");
        assertThat(entity.getStatus()).isEqualTo(GameMachineStatus.IN_USE);

        Game back = mapper.toDomain(entity);
        assertThat(back).usingRecursiveComparison().isEqualTo(domain);
    }

    @Test
    void toDomainNullReturnsNull() {
        assertThat(mapper.toDomain(null)).isNull();
    }

    @Test
    void toEntityNullReturnsNull() {
        assertThat(mapper.toEntity(null)).isNull();
    }

    @Test
    void toDomainCopiesEntityVersion() {
        GameJpaEntity persisted = new GameJpaEntity(
            "game-1", "CHESS", "Scacchi", "b-1", GameMachineStatus.IN_USE);
        persisted.setVersion(7L);

        Game domain = mapper.toDomain(persisted);

        assertThat(domain.getVersion()).isEqualTo(7L);
    }

    @Test
    void toDomainFallsBackToZeroWhenEntityVersionIsNull() {
        GameJpaEntity fresh = new GameJpaEntity(
            "game-1", "CHESS", "Scacchi", "b-1", GameMachineStatus.AVAILABLE);

        assertThat(mapper.toDomain(fresh).getVersion()).isEqualTo(0L);
    }

    @Test
    void toEntitySetsVersionForNewDomainAndExisting() {
        Game fresh = new Game(
            new GameId("game-1"), GameType.CHESS, "Scacchi",
            new BuildingId("b-1"), GameMachineStatus.AVAILABLE);
        assertThat(mapper.toEntity(fresh).getVersion()).isEqualTo(0L);

        Game existing = new Game(
            new GameId("game-1"), GameType.CHESS, "Scacchi",
            new BuildingId("b-1"), GameMachineStatus.IN_USE, 5L);
        assertThat(mapper.toEntity(existing).getVersion()).isEqualTo(5L);
    }
}
