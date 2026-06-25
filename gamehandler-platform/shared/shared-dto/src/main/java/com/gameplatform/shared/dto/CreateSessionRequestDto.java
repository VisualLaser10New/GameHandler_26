package com.gameplatform.shared.dto;

import com.gameplatform.shared.domain.model.GameType;
import java.util.List;

public record CreateSessionRequestDto(
    String gameId,
    GameType gameType,
    List<String> participants,
    String reservationId
) {}
