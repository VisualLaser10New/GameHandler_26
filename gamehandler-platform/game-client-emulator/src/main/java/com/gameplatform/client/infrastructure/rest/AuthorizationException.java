package com.gameplatform.client.infrastructure.rest;

/**
 * Raised by {@link ApiClient} when the Local Server responds with a
 * {@code 403 Forbidden} (authenticated principal missing the role
 * required by the endpoint). The UI surfaces a non-blocking banner
 * explaining the missing permission; the current navigation is kept.
 */
public class AuthorizationException extends RuntimeException {
    public AuthorizationException(String message) {
        super(message);
    }
}