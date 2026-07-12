package com.gameplatform.central.application.service;

import com.gameplatform.central.domain.model.PlayerStatistics;
import com.gameplatform.central.domain.ports.out.PlayerStatisticsRepository;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.PlayerStatisticsDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PlayerStatisticsService}, the Central read-side use
 * case for a player's personal statistics (FASE 3, PIANO &sect;2.4). Pure Mockito
 * (no Spring context), mirroring {@code GameDefinitionServiceTest}.
 *
 * <p>Covers the all-game-types read, the single-game-type filtered read, the
 * empty-result contract for a player who has played no matches, and the null
 * guard on {@code userId}.</p>
 */
@ExtendWith(MockitoExtension.class)
class PlayerStatisticsServiceTest {

    private static final Instant T1 = Instant.parse("2026-07-10T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-07-12T09:30:00Z");
    private static final UserId USER = new UserId("user-1");

    @Mock
    private PlayerStatisticsRepository repository;

    private PlayerStatisticsService service;

    @BeforeEach
    void setUp() {
        service = new PlayerStatisticsService(repository);
    }

    @Test
    void getStatistics_allGameTypes_mapsEveryRowToDto() {
        PlayerStatistics chess = new PlayerStatistics(USER, GameType.CHESS, 3, 2, T1);
        PlayerStatistics darts = new PlayerStatistics(USER, GameType.DARTS, 5, 1, T2);
        when(repository.findByUserId(USER)).thenReturn(List.of(chess, darts));

        List<PlayerStatisticsDto> result = service.getStatistics(USER, null);

        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(
                new PlayerStatisticsDto(USER.value(), GameType.CHESS, 3, 2, T1),
                new PlayerStatisticsDto(USER.value(), GameType.DARTS, 5, 1, T2));
        verify(repository).findByUserId(USER);
    }

    @Test
    void getStatistics_singleGameType_delegatesToFilteredRead() {
        PlayerStatistics chess = new PlayerStatistics(USER, GameType.CHESS, 3, 2, T1);
        when(repository.findByUserIdAndGameType(USER, GameType.CHESS)).thenReturn(Optional.of(chess));

        List<PlayerStatisticsDto> result = service.getStatistics(USER, GameType.CHESS);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo(USER.value());
        assertThat(result.get(0).gameType()).isEqualTo(GameType.CHESS);
        assertThat(result.get(0).matchesPlayed()).isEqualTo(3);
        assertThat(result.get(0).matchesWon()).isEqualTo(2);
        assertThat(result.get(0).lastPlayedAt()).isEqualTo(T1);
        verify(repository).findByUserIdAndGameType(USER, GameType.CHESS);
    }

    @Test
    void getStatistics_singleGameTypeAbsent_returnsEmptyList_notException() {
        when(repository.findByUserIdAndGameType(USER, GameType.ROULETTE)).thenReturn(Optional.empty());

        List<PlayerStatisticsDto> result = service.getStatistics(USER, GameType.ROULETTE);

        assertThat(result).isEmpty();
    }

    @Test
    void getStatistics_userWithNoMatches_returnsEmptyList_notException() {
        when(repository.findByUserId(USER)).thenReturn(List.of());

        List<PlayerStatisticsDto> result = service.getStatistics(USER, null);

        assertThat(result).isEmpty();
    }

    @Test
    void getStatistics_nullUserId_throws() {
        assertThatThrownBy(() -> service.getStatistics(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
    }
}