package com.gameplatform.shared.mqtt.payload;

import com.gameplatform.shared.domain.model.GameType;

public record LobbyCreatePayload(
    GameType gameType,
    String creatorId
) {}
