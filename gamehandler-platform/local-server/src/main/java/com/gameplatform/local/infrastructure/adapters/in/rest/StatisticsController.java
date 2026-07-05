package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.model.LocalStatistics;
import com.gameplatform.local.domain.ports.in.GetStatisticsUseCase;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.GameSessionDto;
import com.gameplatform.shared.dto.StatisticsDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static com.gameplatform.local.infrastructure.adapters.in.rest.GameSessionController.getGameSessionDto;

@RestController
@PreAuthorize("hasRole('USER')")
public class StatisticsController {

    private final GetStatisticsUseCase getStatisticsUseCase;
    private final ObjectMapper objectMapper;
    private final String buildingId;

    public StatisticsController(
            GetStatisticsUseCase getStatisticsUseCase,
            ObjectMapper objectMapper,
            @Value("${app.building-id}") String buildingId) {
        this.getStatisticsUseCase = getStatisticsUseCase;
        this.objectMapper = objectMapper;
        this.buildingId = buildingId;
    }

    @GetMapping("/api/statistics")
    public ResponseEntity<?> getStats(@RequestParam(value = "gameType", required = false) String gameTypeStr) {
        if (gameTypeStr == null || gameTypeStr.isBlank()) {
            Instant now = Instant.now();
            List<StatisticsDto> list = Arrays.stream(GameType.values())
                    .map(gt -> {
                        LocalStatistics stats = getStatisticsUseCase.getStatistics(gt);
                        String serializedWinRates = "";
                        try {
                            serializedWinRates = objectMapper.writeValueAsString(stats.getWinRateByUser());
                        } catch (Exception ignored) {}
                        return new StatisticsDto(
                                buildingId,
                                gt.name(),
                                now.minus(Duration.ofDays(30)),
                                now,
                                stats.getTotalSessions(),
                                (int) Math.round(stats.getAvgDuration()),
                                stats.getTotalReservations(),
                                serializedWinRates
                        );
                    })
                    .toList();
            return ResponseEntity.ok(list);
        }
        GameType gameType = GameType.valueOf(gameTypeStr.toUpperCase());
        LocalStatistics statistics = getStatisticsUseCase.getStatistics(gameType);
        return ResponseEntity.ok(statistics);
    }

    @GetMapping("/api/sessions/active")
    public ResponseEntity<List<GameSessionDto>> getActiveSessions() {
        List<GameSession> activeSessions = getStatisticsUseCase.getActiveSessions();
        List<GameSessionDto> dtos = activeSessions.stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    private GameSessionDto toDto(GameSession session) {
		return getGameSessionDto(session, objectMapper);
	}
}
