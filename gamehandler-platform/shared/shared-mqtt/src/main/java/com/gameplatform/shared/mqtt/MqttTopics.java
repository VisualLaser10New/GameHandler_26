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
