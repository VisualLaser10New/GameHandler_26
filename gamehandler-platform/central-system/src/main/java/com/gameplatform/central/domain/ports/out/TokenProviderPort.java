package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.User;
import java.time.Instant;

/**
 * Port interface for generating authentication tokens.
 * Abstracts security token details from the application domain/service layer.
 */
public interface TokenProviderPort {
    /**
     * Generates a signed token for the authenticated user, starting at the given timestamp.
     *
     * @param user the authenticated user model
     * @param now  the starting timestamp
     * @return the generated token string
     */
    String generateToken(User user, Instant now);

    /**
     * Returns the token expiration duration configured on the provider in milliseconds.
     *
     * @return token lifetime in milliseconds
     */
    long getTokenExpirationMs();
}
