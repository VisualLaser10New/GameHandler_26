package com.gameplatform.client.infrastructure.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Central {@link ObjectMapper} configuration shared by every REST
 * adapter in the client (PIANO §7.C line 725 — "deserializzazione tipata
 * centralizzata").
 * <p>
 * The mapper registers {@link JavaTimeModule} so the {@code Instant}
 * fields carried by the §7.B read DTOs ({@code TournamentSummaryDto},
 * {@code AdminRequestDto}, {@code PlayerMatchDto}, …) round-trip without
 * a custom deserializer.
 */
public final class ObjectMappers {

    /** Shared immutable mapper with {@code JavaTimeModule} registered. */
    public static final ObjectMapper SHARED = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private ObjectMappers() {}
}