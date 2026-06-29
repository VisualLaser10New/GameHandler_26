package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.User;
import java.time.Instant;

/**
 * Port interface for generating authentication tokens in local-server.
 * Abstracts security token details from the application domain/service layer.
 */
public interface TokenGeneratorPort {
    /**
     * Generates a signed token for the authenticated user, starting at the given timestamp.
     *
     * @param user the authenticated user model
     * @param now  the starting timestamp
     * @return the generated token string
     */
    String generateToken(User user, Instant now);
}
