package com.gameplatform.local.domain.exception;

public class ReservationAlreadyUsedException extends RuntimeException {
    public ReservationAlreadyUsedException(String message) {
        super(message);
    }
}
