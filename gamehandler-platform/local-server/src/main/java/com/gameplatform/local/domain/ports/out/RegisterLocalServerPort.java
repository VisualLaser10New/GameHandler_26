package com.gameplatform.local.domain.ports.out;

/**
 * Outbound port for self-registration of the local server against the central system.
 *
 * <p>Implemented by an REST adapter that calls {@code POST /internal/servers/register}
 * on the central system. The central registry is idempotent (upsert by buildingId),
 * so invoking this multiple times is safe.</p>
 */
public interface RegisterLocalServerPort {
    /**
     * Registers (or refreshes) this local server in the central registry.
     *
     * @return {@code true} if the central system acknowledged the registration (HTTP 2xx),
     *         {@code false} otherwise (network error, non-2xx response).
     */
    boolean register();
}
