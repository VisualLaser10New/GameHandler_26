package com.gameplatform.shared.mqtt.payload;

import java.util.Map;

/**
 * MQTT payload broadcast on {@code building/{id}/game/{gameId}/session/score}
 * whenever a player's score changes in a multiplayer game (e.g. Darts,
 * Foosball). Clients subscribed to the topic apply the remote score
 * update so every emulator shows the same scoreboard.
 *
 * <p>Carries a full snapshot of {@code player -> score} entries rather
 * than a delta, so a client joining mid-sequence or losing an earlier
 * message still converges to the correct totals.</p>
 */
public record ScorePayload(
        String sessionId,
        Map<String, Integer> scores
) {}
