package com.gameplatform.client.domain.exception;

import com.gameplatform.client.infrastructure.rest.ApiClient;

/**
 * Raised by {@link ApiClient} when the Local Server is unreachable
 * (connection refused / timeout) or responds with a {@code 5xx} status.
 * The UI renders the global error page with a retry callback
 * (PIANO §7.C line 757).
 */
public class ServerUnavailableException extends RuntimeException {
    public ServerUnavailableException(String message) {
        super(message);
    }

    public ServerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}