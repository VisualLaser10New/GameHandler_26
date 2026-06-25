package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.model.LocalStatistics;
import com.gameplatform.local.domain.ports.in.GetStatisticsUseCase;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.GameSessionDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.gameplatform.local.infrastructure.adapters.in.rest.GameSessionController.getGameSessionDto;

@RestController
@PreAuthorize("hasRole('USER')")
public class StatisticsController {

    private final GetStatisticsUseCase getStatisticsUseCase;
    private final ObjectMapper objectMapper;

    public StatisticsController(GetStatisticsUseCase getStatisticsUseCase, ObjectMapper objectMapper) {
        this.getStatisticsUseCase = getStatisticsUseCase;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/api/statistics")
    public ResponseEntity<LocalStatistics> getStats(@RequestParam("gameType") String gameTypeStr) {
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
