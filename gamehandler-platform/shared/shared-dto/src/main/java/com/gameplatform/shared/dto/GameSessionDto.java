package main.java.com.gameplatform.shared.dto;

import java.time.Instant;
import main.java.com.gameplatform.shared.domain.model.GameType;
import main.java.com.gameplatform.shared.domain.model.GameStatus;
import main.java.com.gameplatform.shared.domain.model.WinCondition;

public record GameSessionDto(
    String id,
    String gameId,
    GameType gameType,
    GameStatus status,
    Instant startedAt,
    Instant endedAt,
    Integer durationSeconds,
    String winnerId,
    WinCondition winCondition,
    String resultData
) {}
