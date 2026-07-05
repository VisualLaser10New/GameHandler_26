package com.gameplatform.shared.mqtt.payload;

/**
 * MQTT payload broadcast on {@code building/{id}/game/{gameId}/session/turn}
 * whenever a player ends their turn in a turn-based multiplayer game
 * (Chess, Risk, Darts, Monopoly). Clients subscribed to the topic apply
 * the remote turn update so every emulator shows the same active player
 * and only the player whose turn it is can act.
 */
public record TurnPayload(
        String sessionId,
        int turnIndex,
        String playerName
) {}
