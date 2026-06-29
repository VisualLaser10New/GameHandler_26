package com.gameplatform.central.domain.model;

public record ReplicationProgress(String eventId, String serverId) {
    public ReplicationProgress {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId cannot be null or blank");
        }
        if (serverId == null || serverId.isBlank()) {
            throw new IllegalArgumentException("serverId cannot be null or blank");
        }
    }
}
