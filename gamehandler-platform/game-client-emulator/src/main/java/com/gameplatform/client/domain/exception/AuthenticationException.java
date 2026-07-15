package com.gameplatform.client.domain.exception;

import com.gameplatform.client.infrastructure.rest.ApiClient;

/**
 * Raised by {@link ApiClient} when the Local Server responds with a
 * {@code 401 Unauthorized} (token missing, expired or invalid).
 * <p>
 * The UI layer is expected to redirect the user to the {@code LoginView}
 * and clear the session state.
 */
public class AuthenticationException extends RuntimeException {
    public AuthenticationException(String message) {
        super(message);
    }
}