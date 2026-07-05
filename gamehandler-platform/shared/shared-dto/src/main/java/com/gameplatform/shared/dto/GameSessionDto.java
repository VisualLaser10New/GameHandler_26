package com.gameplatform.shared.dto;

import java.time.Instant;
import java.util.List;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.WinCondition;

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
    String resultData,
    List<String> participants
) {
    /**
     * Backwards-compatible canonical constructor: allows callers and JSON
     * deserialization to omit {@code participants} (defaults to an empty
     * list) so that existing code/tests that built the DTO without this
     * field keep working.
     */
    public GameSessionDto {
        if (participants == null) {
            participants = List.of();
        }
    }

    /** Convenience constructor without participants (defaults to empty). */
    public GameSessionDto(String id, String gameId, GameType gameType, GameStatus status,
                          Instant startedAt, Instant endedAt, Integer durationSeconds,
                          String winnerId, WinCondition winCondition, String resultData) {
        this(id, gameId, gameType, status, startedAt, endedAt, durationSeconds,
                winnerId, winCondition, resultData, List.of());
    }
}
