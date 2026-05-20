package main.java.com.gameplatform.shared.domain.model;

public record GameSessionId(String value) {
    public GameSessionId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("GameSessionId cannot be null");
        }
    }
}
