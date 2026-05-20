package com.gameplatform.central.infrastructure.adapters.in.rest;

import com.gameplatform.central.domain.ports.in.GetGlobalStatisticsUseCase;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.StatisticsDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/statistics")
@PreAuthorize("hasRole('ADMIN')")
public class StatisticsController {

    private final GetGlobalStatisticsUseCase getGlobalStatisticsUseCase;

    public StatisticsController(GetGlobalStatisticsUseCase getGlobalStatisticsUseCase) {
        this.getGlobalStatisticsUseCase = getGlobalStatisticsUseCase;
    }

    @GetMapping
    public ResponseEntity<List<StatisticsDto>> getStatistics(
            @RequestParam(required = false) String buildingId,
            @RequestParam(required = false) String gameType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        BuildingId parsedBuildingId = (buildingId != null && !buildingId.isBlank())
                ? new BuildingId(buildingId)
                : null;

        GameType parsedGameType = (gameType != null && !gameType.isBlank())
                ? GameType.valueOf(gameType.toUpperCase())
                : null;

        List<StatisticsDto> statistics = getGlobalStatisticsUseCase.getStatistics(
                parsedBuildingId,
                parsedGameType,
                start,
                end
        );

        return ResponseEntity.ok(statistics);
    }
}

