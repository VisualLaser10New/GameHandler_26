package com.gameplatform.local.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gameplatform.local.domain.exception.InvalidGameStateTransitionException;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.domain.model.GameType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GameTest {

    private static Game sample(GameMachineStatus status) {
        return new Game(new GameId("game-1"), GameType.CHESS, "Chess 1",
                new BuildingId("building-1"), status);
    }

    @Nested
    class Construction {

        @Test
        void shouldCreateGameSuccessfully() {
            GameId gameId = new GameId("game-1");
            BuildingId buildingId = new BuildingId("building-1");

            Game game = new Game(gameId, GameType.CHESS, "Chess Table 1", buildingId,
                    GameMachineStatus.AVAILABLE);

            assertThat(game.getId()).isEqualTo(gameId);
            assertThat(game.getGameType()).isEqualTo(GameType.CHESS);
            assertThat(game.getName()).isEqualTo("Chess Table 1");
            assertThat(game.getBuildingId()).isEqualTo(buildingId);
            assertThat(game.getStatus()).isEqualTo(GameMachineStatus.AVAILABLE);
        }

        @Test
        void shouldRejectAnyNullRequiredField() {
            GameId id = new GameId("g");
            GameType type = GameType.CHESS;
            BuildingId building = new BuildingId("b");
            GameMachineStatus status = GameMachineStatus.AVAILABLE;

            assertThatThrownBy(() -> new Game(null, type, "n", building, status))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Game(id, null, "n", building, status))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Game(id, type, null, building, status))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Game(id, type, "  ", building, status))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Game(id, type, "n", null, status))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Game(id, type, "n", building, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectEmptyName() {
            assertThatThrownBy(() -> new Game(new GameId("g"), GameType.CHESS, "",
                    new BuildingId("b"), GameMachineStatus.AVAILABLE))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldAcceptAllGameTypes() {
            for (GameType type : GameType.values()) {
                Game game = new Game(new GameId("g"), type, "n", new BuildingId("b"),
                        GameMachineStatus.AVAILABLE);
                assertThat(game.getGameType()).isEqualTo(type);
            }
        }

        @Test
        void shouldAcceptAnyInitialStatus() {
            for (GameMachineStatus status : GameMachineStatus.values()) {
                Game game = new Game(new GameId("g"), GameType.CHESS, "n", new BuildingId("b"), status);
                assertThat(game.getStatus()).isEqualTo(status);
            }
        }
    }

    @Nested
    class Reserve {

        @Test
        void shouldReserveAvailableGame() {
            Game game = sample(GameMachineStatus.AVAILABLE);
            game.reserve();
            assertThat(game.getStatus()).isEqualTo(GameMachineStatus.RESERVED);
        }

        @Test
        void shouldFailReservingNonAvailableGame() {
            assertThatThrownBy(() -> sample(GameMachineStatus.RESERVED).reserve())
                    .isInstanceOf(InvalidGameStateTransitionException.class)
                    .hasMessageContaining("RESERVED");
            assertThatThrownBy(() -> sample(GameMachineStatus.IN_USE).reserve())
                    .isInstanceOf(InvalidGameStateTransitionException.class);
            assertThatThrownBy(() -> sample(GameMachineStatus.MAINTENANCE).reserve())
                    .isInstanceOf(InvalidGameStateTransitionException.class);
        }

        @Test
        void shouldNotReserveTwiceInARow() {
            Game game = sample(GameMachineStatus.AVAILABLE);
            game.reserve();
            assertThatThrownBy(game::reserve)
                    .isInstanceOf(InvalidGameStateTransitionException.class);
        }
    }

    @Nested
    class StartUse {

        @Test
        void shouldStartUseFromAvailable() {
            Game game = sample(GameMachineStatus.AVAILABLE);
            game.startUse();
            assertThat(game.getStatus()).isEqualTo(GameMachineStatus.IN_USE);
        }

        @Test
        void shouldStartUseFromReserved() {
            Game game = sample(GameMachineStatus.RESERVED);
            game.startUse();
            assertThat(game.getStatus()).isEqualTo(GameMachineStatus.IN_USE);
        }

        @Test
        void shouldFailStartingUseFromInUseOrMaintenance() {
            assertThatThrownBy(() -> sample(GameMachineStatus.IN_USE).startUse())
                    .isInstanceOf(InvalidGameStateTransitionException.class);
            assertThatThrownBy(() -> sample(GameMachineStatus.MAINTENANCE).startUse())
                    .isInstanceOf(InvalidGameStateTransitionException.class);
        }

        @Test
        void shouldFailStartingUseTwiceFromInUse() {
            Game game = sample(GameMachineStatus.AVAILABLE);
            game.startUse();
            assertThatThrownBy(game::startUse)
                    .isInstanceOf(InvalidGameStateTransitionException.class);
        }
    }

    @Nested
    class Release {

        @Test
        void shouldReleaseFromReservedInUseAndMaintenance() {
            assertThat(sample(GameMachineStatus.RESERVED).getStatus()).describedAs("sanity")
                    .isEqualTo(GameMachineStatus.RESERVED);

            Game reserved = sample(GameMachineStatus.RESERVED);
            reserved.release();
            assertThat(reserved.getStatus()).isEqualTo(GameMachineStatus.AVAILABLE);

            Game inUse = sample(GameMachineStatus.IN_USE);
            inUse.release();
            assertThat(inUse.getStatus()).isEqualTo(GameMachineStatus.AVAILABLE);

            Game maintenance = sample(GameMachineStatus.MAINTENANCE);
            maintenance.release();
            assertThat(maintenance.getStatus()).isEqualTo(GameMachineStatus.AVAILABLE);
        }

        @Test
        void shouldBeIdempotentWhenAlreadyAvailable() {
            Game game = sample(GameMachineStatus.AVAILABLE);
            game.release();
            assertThat(game.getStatus()).isEqualTo(GameMachineStatus.AVAILABLE);
        }

        @Test
        void releaseCoversAllStatusesWithoutThrowing() {
            for (GameMachineStatus status : GameMachineStatus.values()) {
                Game game = sample(status);
                game.release();
                assertThat(game.getStatus()).isEqualTo(GameMachineStatus.AVAILABLE);
            }
        }

        @Test
        void releasingAnAlreadyAvailableGameDoesNotChangeAnything() {
            Game game = sample(GameMachineStatus.AVAILABLE);
            game.release();
            game.release();
            assertThat(game.getStatus()).isEqualTo(GameMachineStatus.AVAILABLE);
        }
    }

    @Nested
    class Maintenance {

        @Test
        void shouldSetMaintenanceFromAnyState() {
            for (GameMachineStatus status : GameMachineStatus.values()) {
                Game game = sample(status);
                game.setMaintenance();
                assertThat(game.getStatus()).isEqualTo(GameMachineStatus.MAINTENANCE);
            }
        }

        @Test
        void setMaintenanceIsPermissiveEvenFromInUse() {
            Game game = sample(GameMachineStatus.IN_USE);
            game.setMaintenance();
            assertThat(game.getStatus()).isEqualTo(GameMachineStatus.MAINTENANCE);
        }

        @Test
        void setMaintenanceOverridesReserved() {
            Game game = sample(GameMachineStatus.RESERVED);
            game.setMaintenance();
            assertThat(game.getStatus()).isEqualTo(GameMachineStatus.MAINTENANCE);
        }

        @Test
        void setMaintenanceIsIdempotent() {
            Game game = sample(GameMachineStatus.AVAILABLE);
            game.setMaintenance();
            game.setMaintenance();
            assertThat(game.getStatus()).isEqualTo(GameMachineStatus.MAINTENANCE);
        }

        @Test
        void canReturnFromMaintenanceToAvailableViaRelease() {
            Game game = sample(GameMachineStatus.AVAILABLE);
            game.setMaintenance();
            game.release();
            assertThat(game.getStatus()).isEqualTo(GameMachineStatus.AVAILABLE);
        }
    }

    @Nested
    class Lifecycle {

        @Test
        void shouldSupportFullLifecycleAvailableReserveInUseReleaseAvailable() {
            Game game = sample(GameMachineStatus.AVAILABLE);
            game.reserve();
            assertThat(game.getStatus()).isEqualTo(GameMachineStatus.RESERVED);
            game.startUse();
            assertThat(game.getStatus()).isEqualTo(GameMachineStatus.IN_USE);
            game.release();
            assertThat(game.getStatus()).isEqualTo(GameMachineStatus.AVAILABLE);
        }

        @Test
        void shouldSupportDirectStartUseWithoutExplicitReserve() {
            Game game = sample(GameMachineStatus.AVAILABLE);
            game.startUse();
            assertThat(game.getStatus()).isEqualTo(GameMachineStatus.IN_USE);
            game.release();
            assertThat(game.getStatus()).isEqualTo(GameMachineStatus.AVAILABLE);
        }

        @Test
        void shouldSupportReserveThenReleaseBackToAvailable() {
            Game game = sample(GameMachineStatus.AVAILABLE);
            game.reserve();
            game.release();
            assertThat(game.getStatus()).isEqualTo(GameMachineStatus.AVAILABLE);
        }
    }

    @Nested
    class Equality {

        @Test
        void gamesDoNotOverrideEqualsSoIdentityEqualityHolds() {
            Game a = sample(GameMachineStatus.AVAILABLE);
            Game b = sample(GameMachineStatus.AVAILABLE);
            assertThat(a).isNotSameAs(b);
            assertThat(a.equals(b)).isFalse();
            assertThat(a.equals(a)).isTrue();
            assertThat(a.equals(null)).isFalse();
        }
    }
}
