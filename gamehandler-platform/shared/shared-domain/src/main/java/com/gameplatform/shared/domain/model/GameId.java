package main.java.com.gameplatform.shared.domain.model;

public record GameId(String id) {
    public GameId {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("GameId cannot be null");
    }
}
