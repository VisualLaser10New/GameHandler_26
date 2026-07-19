package com.gameplatform.local.domain.model;

/**
 * Ciclo di vita di un evento nella coda di outbox.
 * Un evento viene creato come {@link #PENDING}, transita a {@link #SENT}
 * dopo l'invio riuscito, oppure a {@link #FAILED} dopo il superamento
 * della soglia di tentativi.
 *
 * @see OutboxEvent
 */
public enum OutboxEventStatus {
    PENDING,
    SENT,
    FAILED
}