package com.gameplatform.shared.mqtt.payload;

import java.util.List;
import com.gameplatform.shared.domain.model.GameType;

public record SessionStartPayload(
    String sessionId,
    GameType gameType,
    List<String> participants
) {}
