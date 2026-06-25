package com.gameplatform.local.domain.exception;

public class ReservationExpiredException extends RuntimeException {
    public ReservationExpiredException(String message) {
        super(message);
    }
}
