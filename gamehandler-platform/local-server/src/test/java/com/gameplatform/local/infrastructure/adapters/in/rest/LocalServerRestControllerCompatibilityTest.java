package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.model.Reservation;
import com.gameplatform.local.domain.ports.in.GetAvailableGamesUseCase;
import com.gameplatform.local.application.service.ReservationService;
import com.gameplatform.shared.domain.model.*;
import com.gameplatform.shared.domain.result.ChessResult;
import com.gameplatform.shared.domain.result.GameResult;
import com.gameplatform.shared.dto.GameStateDto;
import com.gameplatform.shared.dto.ReservationDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * Targeted compatibility tests that verify the shared-dto contracts
 * in the REST adapter layer of local-server.
 */
@ExtendWith(MockitoExtension.class)
class LocalServerRestControllerCompatibilityTest {

    @Mock
    private GetAvailableGamesUseCase getAvailableGamesUseCase;

    @Mock
    private ReservationService reservationService;

    private GameController gameController;
    private ReservationController reservationController;

    @BeforeEach
    void setUp() {
        gameController = new GameController(getAvailableGamesUseCase);
        reservationController = new ReservationController(reservationService, reservationService, reservationService);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GameController DTO mapping
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GameController → GameStateDto mapping")
    class GameStateDtoMapping {

        @Test
        @DisplayName("GameStateDto is built from Game domain model with all shared-domain fields")
        void gameStateDtoMapping() {
            Game game = new Game(
                    new GameId("g-1"), GameType.SLOT_MACHINE, "Slot Machine 1",
                    new BuildingId("bld-1"), GameMachineStatus.AVAILABLE
            );
            given(getAvailableGamesUseCase.getAll()).willReturn(List.of(game));

            var response = gameController.getGames();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            List<GameStateDto> body = response.getBody();
            assertThat(body).hasSize(1);
            GameStateDto dto = body.get(0);
            assertThat(dto.gameId()).isEqualTo("g-1");
            assertThat(dto.gameType()).isEqualTo(GameType.SLOT_MACHINE);
            assertThat(dto.name()).isEqualTo("Slot Machine 1");
            assertThat(dto.buildingId()).isEqualTo("bld-1");
            assertThat(dto.status()).isEqualTo(GameMachineStatus.AVAILABLE);
        }

        @Test
        @DisplayName("getAvailableGames filters correctly by AVAILABLE status")
        void availableGamesFiltering() {
            Game available = new Game(new GameId("g-1"), GameType.CHESS, "C1",
                    new BuildingId("b-1"), GameMachineStatus.AVAILABLE);
            Game inUse = new Game(new GameId("g-2"), GameType.CHESS, "C2",
                    new BuildingId("b-1"), GameMachineStatus.IN_USE);
            given(getAvailableGamesUseCase.getAvailable()).willReturn(List.of(available));

            var response = gameController.getAvailableGames();
            assertThat(response.getBody()).singleElement().satisfies(dto -> {
                assertThat(dto.status()).isEqualTo(GameMachineStatus.AVAILABLE);
            });
            // Ensure in-use games are not returned
            given(getAvailableGamesUseCase.getAvailable()).willReturn(List.of());
            assertThat(gameController.getAvailableGames().getBody()).isEmpty();
        }

        @Test
        @DisplayName("GameStateDto fields cover all five shared-dto fields exactly")
        void gameStateDtoAllFieldsPresent() {
            Game game = new Game(
                    new GameId("g-1"), GameType.ROULETTE, "Roulette Table",
                    new BuildingId("b-1"), GameMachineStatus.MAINTENANCE
            );
            given(getAvailableGamesUseCase.getAll()).willReturn(List.of(game));

            GameStateDto dto = gameController.getGames().getBody().get(0);
            assertThat(dto).hasNoNullFieldsOrProperties();
            assertThat(dto.toString()).contains("Roulette Table");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ReservationController DTO mapping
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ReservationController → ReservationDto mapping")
    class ReservationDtoMapping {

        @Test
        @DisplayName("ReservationDto fields match shared-dto order and types exactly")
        void reservationDtoFieldExactMatch() {
            Reservation reservation = new Reservation(
                    new ReservationId("res-1"),
                    new GameId("g-1"),
                    new UserId("u-1"),
                    ReservationStatus.PENDING,
                    Instant.parse("2026-06-27T14:00:00Z"),
                    Instant.parse("2026-06-27T15:00:00Z"),
                    Instant.parse("2026-06-27T10:00:00Z")
            );
            given(reservationService.getByUser(new UserId("u-1"))).willReturn(List.of(reservation));

            var response = reservationController.getByUser("u-1");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            List<ReservationDto> dtos = response.getBody();
            assertThat(dtos).hasSize(1);
            ReservationDto dto = dtos.get(0);
            assertThat(dto.id()).isEqualTo("res-1");
            assertThat(dto.gameId()).isEqualTo("g-1");
            assertThat(dto.userId()).isEqualTo("u-1");
            assertThat(dto.status()).isEqualTo(ReservationStatus.PENDING);
            assertThat(dto.startTime()).isEqualTo(Instant.parse("2026-06-27T14:00:00Z"));
            assertThat(dto.endTime()).isEqualTo(Instant.parse("2026-06-27T15:00:00Z"));
        }
    }
}
