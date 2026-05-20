package main.java.com.gameplatform.shared.domain.model;

public record ReservationId(String value) {
    public ReservationId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ReservationId cannot be null");
        }
    }
}
