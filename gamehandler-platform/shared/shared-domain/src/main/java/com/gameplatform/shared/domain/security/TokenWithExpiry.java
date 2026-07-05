package com.gameplatform.shared.domain.security;

import java.time.Instant;

/**
 * Carries both the compact JWT string and the {@code exp} claim that was embedded
 * in it, so callers (e.g. {@code AuthService.authenticate}) can use a single
 * source of truth for the token's expiration (fix for BUG-AUTH-01 / B11).
 *
 * <p>Placed in {@code shared-domain} so that both {@code central-system}'s
 * {@code TokenProviderPort} and {@code local-server}'s {@code TokenGeneratorPort}
 * can share the same return type without duplicating the record.</p>
 *
 * @param token     compact JWT string
 * @param expiresAt the {@code exp} claim embedded in the JWT
 */
public record TokenWithExpiry(String token, Instant expiresAt) {
    public TokenWithExpiry {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token cannot be null or blank");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt cannot be null");
        }
    }
}
