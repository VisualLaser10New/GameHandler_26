package com.gameplatform.shared.mqtt;

public final class MqttTopics {
    private MqttTopics() {}

    public static String gameState(String buildingId, String gameId) {
        return "building/" + buildingId + "/game/" + gameId + "/state";
    }

    public static String sessionStart(String buildingId, String gameId) {
        return "building/" + buildingId + "/game/" + gameId + "/session/start";
    }

    public static String sessionEnd(String buildingId, String gameId) {
        return "building/" + buildingId + "/game/" + gameId + "/session/end";
    }

    public static String sessionPause(String buildingId, String gameId) {
        return "building/" + buildingId + "/game/" + gameId + "/session/pause";
    }

    public static String sessionResume(String buildingId, String gameId) {
        return "building/" + buildingId + "/game/" + gameId + "/session/resume";
    }

    /**
     * Topic used by turn-based multiplayer game clients to broadcast
     * turn changes (Chess, Risk, Darts, Monopoly). When a player ends
     * their turn, every subscribed emulator receives the new turn index
     * and active player name so all clients stay in sync.
     */
    public static String sessionTurn(String buildingId, String gameId) {
        return "building/" + buildingId + "/game/" + gameId + "/session/turn";
    }

    /**
     * Topic used by board-style multiplayer game clients to broadcast
     * individual piece moves (currently Chess). When a player moves a
     * piece, every subscribed emulator receives the move and applies
     * it so all clients show the same board state.
     */
    public static String sessionMove(String buildingId, String gameId) {
        return "building/" + buildingId + "/game/" + gameId + "/session/move";
    }

    /**
     * Topic used by multiplayer game clients to broadcast score
     * updates (e.g. Darts, Foosball). When a player's score changes,
     * every subscribed emulator receives the full score snapshot so
     * all clients show the same scoreboard.
     */
    public static String sessionScore(String buildingId, String gameId) {
        return "building/" + buildingId + "/game/" + gameId + "/session/score";
    }

    public static String heartbeat(String buildingId, String gameId) {
        return "building/" + buildingId + "/game/" + gameId + "/heartbeat";
    }

    public static String heartbeatAck(String buildingId, String gameId) {
        return "building/" + buildingId + "/game/" + gameId + "/heartbeat/ack";
    }

    public static String alerts(String buildingId) {
        return "building/" + buildingId + "/alerts";
    }
}
