package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.User;
import com.gameplatform.shared.domain.security.TokenWithExpiry;
import java.time.Instant;

/**
 * Port interface for generating authentication tokens.
 * Abstracts security token details from the application domain/service layer.
 */
public interface TokenProviderPort {
    /**
     * Generates a signed token for the authenticated user, starting at the given timestamp.
     *
     * <p><strong>Deprecated since B11.</strong> Use {@link #generateTokenWithExpiry(User, Instant)}
     * instead so the caller receives the exact {@code exp} claim embedded in the JWT —
     * eliminating the dual-clock drift between the token and the advertised
     * {@code LoginResponseDto.expiresAt} (single source of truth).</p>
     *
     * @param user the authenticated user model
     * @param now  the starting timestamp
     * @return the generated token string
     */
    @Deprecated(since = "B11", forRemoval = true)
    String generateToken(User user, Instant now);

    /**
     * Generates a signed token for the authenticated user, starting at the given timestamp,
     * and returns both the compact JWT string and the {@code exp} claim that was embedded
     * in it (single source of truth for the token's expiration — fix for BUG-AUTH-01 / B11).
     *
     * @param user the authenticated user model
     * @param now  the starting timestamp
     * @return a {@link TokenWithExpiry} holding the compact JWT and its {@code exp} claim
     */
    TokenWithExpiry generateTokenWithExpiry(User user, Instant now);

    /**
     * Returns the token expiration duration configured on the provider in milliseconds.
     *
     * @return token lifetime in milliseconds
     */
    long getTokenExpirationMs();
}
