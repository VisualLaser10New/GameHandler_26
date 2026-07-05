package com.gameplatform.local.domain.ports.in;

/**
 * Use case: the local server registers itself with the central system at startup.
 *
 * <p>Implementations handle retry/backoff until the central system is reachable
 * and acknowledges the registration.</p>
 */
public interface RegisterLocalServerUseCase {
    /**
     * Blocking call that tries to register this local server with the central system.
     * Returns {@code true} on success, {@code false} if registration ultimately fails.
     */
    boolean register();
}
