package com.gameplatform.central.infrastructure.security;

import com.gameplatform.central.domain.model.User;
import com.gameplatform.shared.domain.model.UserId;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtTokenProvider}.
 *
 * <ul>
 *   <li>Token generation uses the configurable {@code tokenExpirationMs}.</li>
 *   <li>Generated tokens are valid and parseable.</li>
 *   <li>Expired tokens are rejected by {@code validateToken}.</li>
 *   <li>Tampered tokens fail validation.</li>
 *   <li>Blank / null inputs are handled gracefully.</li>
 * </ul>
 *
 * <p>The provider is initialised without a PEM file, so it falls back to a
 * freshly generated RSA key pair — perfectly valid for unit testing.</p>
 */
@DisplayName("JwtTokenProvider")
class JwtTokenProviderTest {

    private static final long EXPIRATION_MS_24H = 86_400_000L;
    private static final long EXPIRATION_MS_SHORT = 1L; // 1 ms — expires immediately

    private ResourceLoader resourceLoader;

    /** Builds and initialises a provider with the given expiration value. */
    private JwtTokenProvider buildProvider(long expirationMs) {
        // "classpath:non-existent.pem" forces the fallback key pair generation
        JwtTokenProvider provider = new JwtTokenProvider(
                resourceLoader, "classpath:non-existent.pem", expirationMs);
        provider.init();
        return provider;
    }

    private User buildUser(String username) {
        return new User(
                new UserId(UUID.randomUUID().toString()),
                username,
                "$2a$10$fakehashfakehashfakehashfakehashfakeha",
                username + "@example.com",
                List.of("USER"),
                Instant.now()
        );
    }

    @BeforeEach
    void setUp() {
        resourceLoader = new DefaultResourceLoader();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor / configuration
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("configuration")
    class ConfigurationTests {

        @Test
        @DisplayName("getTokenExpirationMs returns the value passed to the constructor")
        void getTokenExpirationMs_returnsConfiguredValue() {
            JwtTokenProvider provider = buildProvider(EXPIRATION_MS_24H);
            assertThat(provider.getTokenExpirationMs()).isEqualTo(EXPIRATION_MS_24H);
        }

        @Test
        @DisplayName("different expiration values are reflected independently")
        void differentExpirationValues_areIndependent() {
            long customMs = 3_600_000L; // 1 hour
            JwtTokenProvider provider = buildProvider(customMs);
            assertThat(provider.getTokenExpirationMs()).isEqualTo(customMs);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Token generation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("generateToken")
    class GenerateTokenTests {

        @Test
        @DisplayName("returns a non-blank compact JWT string")
        void generateToken_returnsNonBlankToken() {
            JwtTokenProvider provider = buildProvider(EXPIRATION_MS_24H);
            String token = provider.generateToken(buildUser("alice"));
            assertThat(token).isNotBlank().contains(".");
        }

        @Test
        @DisplayName("sets expiration approximately at now + tokenExpirationMs")
        void generateToken_setsExpirationToConfiguredValue() {
            JwtTokenProvider provider = buildProvider(EXPIRATION_MS_24H);
            User user = buildUser("alice");

            long before = System.currentTimeMillis();
            String token = provider.generateToken(user);
            long after = System.currentTimeMillis();

            Claims claims = provider.getClaims(token);
            Date expiration = claims.getExpiration();

            assertThat(expiration.getTime())
                    .as("Expiration should be approximately now + 24h")
                    .isBetween(before + EXPIRATION_MS_24H - 1000, after + EXPIRATION_MS_24H + 1000);
        }

        @Test
        @DisplayName("embeds username, userId and roles in claims")
        void generateToken_embedsClaims() {
            JwtTokenProvider provider = buildProvider(EXPIRATION_MS_24H);
            User user = buildUser("bob");
            String token = provider.generateToken(user);
            Claims claims = provider.getClaims(token);

            assertThat(claims.getSubject()).isEqualTo("bob");
            assertThat(claims.get("userId", String.class)).isEqualTo(user.getId().value());
            assertThat(claims.get("roles", java.util.List.class)).containsExactly("USER");
        }

        @Test
        @DisplayName("tokens generated for different users are distinct")
        void generateToken_differentUsers_produceDifferentTokens() {
            JwtTokenProvider provider = buildProvider(EXPIRATION_MS_24H);

            String t1 = provider.generateToken(buildUser("charlie"));
            String t2 = provider.generateToken(buildUser("diana"));

            assertThat(t1).isNotEqualTo(t2);
        }

        @Test
        @DisplayName("token subject matches the generating user's username")
        void generateToken_subjectMatchesUsername() {
            JwtTokenProvider provider = buildProvider(EXPIRATION_MS_24H);
            User user = buildUser("ernest");

            String token = provider.generateToken(user);
            Claims claims = provider.getClaims(token);

            assertThat(claims.getSubject()).isEqualTo("ernest");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // validateToken
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validateToken")
    class ValidateTokenTests {

        @Test
        @DisplayName("returns true for a freshly generated token")
        void validateToken_returnsTrueForValidToken() {
            JwtTokenProvider provider = buildProvider(EXPIRATION_MS_24H);
            String token = provider.generateToken(buildUser("dave"));
            assertThat(provider.validateToken(token)).isTrue();
        }

        @Test
        @DisplayName("returns false for a tampered token")
        void validateToken_returnsFalseForTamperedToken() {
            JwtTokenProvider provider = buildProvider(EXPIRATION_MS_24H);
            String token = provider.generateToken(buildUser("eve"));
            int lastDot = token.lastIndexOf('.');
            String tampered = token.substring(0, lastDot + 1) + "signatureisinvalid";
            assertThat(provider.validateToken(tampered)).isFalse();
        }

        @Test
        @DisplayName("returns false for a completely random string")
        void validateToken_returnsFalseForGarbage() {
            JwtTokenProvider provider = buildProvider(EXPIRATION_MS_24H);
            assertThat(provider.validateToken("not.a.jwt")).isFalse();
        }

        @Test
        @DisplayName("returns false for a blank string")
        void validateToken_returnsFalseForBlank() {
            JwtTokenProvider provider = buildProvider(EXPIRATION_MS_24H);
            assertThat(provider.validateToken("   ")).isFalse();
        }

        @Test
        @DisplayName("returns false for an empty string")
        void validateToken_returnsFalseForEmpty() {
            JwtTokenProvider provider = buildProvider(EXPIRATION_MS_24H);
            assertThat(provider.validateToken("")).isFalse();
        }

        @Test
        @DisplayName("returns false for a token signed by a different key pair")
        void validateToken_returnsFalseForDifferentKeyPair() {
            JwtTokenProvider provider1 = buildProvider(EXPIRATION_MS_24H);
            JwtTokenProvider provider2 = buildProvider(EXPIRATION_MS_24H);

            String tokenFromProvider1 = provider1.generateToken(buildUser("frank"));
            // provider2 has a different key pair — must reject the token
            assertThat(provider2.validateToken(tokenFromProvider1)).isFalse();
        }

        @Test
        @DisplayName("returns false for an expired token (1 ms expiration)")
        void validateToken_returnsFalseForExpiredToken() throws InterruptedException {
            JwtTokenProvider provider = buildProvider(EXPIRATION_MS_SHORT);
            String token = provider.generateToken(buildUser("grace"));

            Thread.sleep(10); // ensure the 1 ms token has expired

            assertThat(provider.validateToken(token)).isFalse();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getClaims — expired token must throw
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getClaims")
    class GetClaimsTests {

        @Test
        @DisplayName("throws ExpiredJwtException for an expired token")
        void getClaims_throwsForExpiredToken() throws InterruptedException {
            JwtTokenProvider provider = buildProvider(EXPIRATION_MS_SHORT);
            String token = provider.generateToken(buildUser("heidi"));
            Thread.sleep(10);

            assertThatThrownBy(() -> provider.getClaims(token))
                    .isInstanceOf(ExpiredJwtException.class);
        }
    }
}
