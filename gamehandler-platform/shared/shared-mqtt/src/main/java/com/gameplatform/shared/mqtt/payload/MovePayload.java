package com.gameplatform.shared.mqtt.payload;

/**
 * MQTT payload broadcast on {@code building/{id}/game/{gameId}/session/move}
 * whenever a player makes a move in a board-style multiplayer game
 * (currently Chess). Clients subscribed to the topic apply the remote
 * move so every emulator shows the same board state.
 *
 * <p>The move is expressed as source/target board coordinates (0-based
 * row/col pairs). An optional {@code capturedPiece} carries the
 * Unicode glyph of the piece that was on the target cell, if any, so
 * the receiving client can record it in its captured-pieces list.</p>
 */
public record MovePayload(
        String sessionId,
        int fromRow,
        int fromCol,
        int toRow,
        int toCol,
        String capturedPiece
) {}
