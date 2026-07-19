package com.gameplatform.client.infrastructure.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Configurazione centralizzata dell'{@link ObjectMapper} condivisa da
 * tutti gli adapter REST del client.
 * <p>
 * Registra il modulo {@link JavaTimeModule} per supportare la
 * serializzazione e deserializzazione dei campi {@link java.time.Instant}
 * presenti nei DTO di lettura del dominio senza necessità di
 * deserializzatori personalizzati.
 * <p>
 * Classe finale con costruttore privato; fornisce solo l'istanza
 * pubblica immutabile {@link #SHARED}.
 */
public final class ObjectMappers {

    /**
     * Istanza immutabile condivisa di {@link ObjectMapper} con
     * {@link JavaTimeModule} registrato.
     */
    public static final ObjectMapper SHARED = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /**
     * Costruttore privato per impedire l'istanziazione della classe.
     */
    private ObjectMappers() {}
}
