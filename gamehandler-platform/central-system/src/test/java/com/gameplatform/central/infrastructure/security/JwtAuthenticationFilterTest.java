package com.gameplatform.central.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JwtAuthenticationFilter}.
 *
 * <ul>
 *   <li>Missing {@code Authorization} header → chain continues, no 401 sent.</li>
 *   <li>Invalid (non-Bearer) scheme → chain continues, no 401 sent.</li>
 *   <li>Invalid JWT (validateToken returns false) → 401 sent, chain halted.</li>
 *   <li>Valid JWT → security context populated, chain continues.</li>
 *   <li>Exception from getClaims after validateToken → 401 sent, chain halted.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter")
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtTokenProvider);
        SecurityContextHolder.clearContext();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Missing / non-Bearer header
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("when Authorization header is absent or non-Bearer")
    class NoHeaderTests {

        @Test
        @DisplayName("passes request to chain when Authorization header is null")
        void missingHeader_chainsThrough() throws Exception {
            when(request.getHeader("Authorization")).thenReturn(null);

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("passes request to chain when Authorization header has non-Bearer scheme")
        void nonBearerHeader_chainsThrough() throws Exception {
            when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("does not invoke JwtTokenProvider when no Bearer token is present")
        void noToken_providerNotInvoked() throws Exception {
            when(request.getHeader("Authorization")).thenReturn(null);

            filter.doFilterInternal(request, response, filterChain);

            verifyNoInteractions(jwtTokenProvider);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Invalid token
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("when Bearer token is invalid")
    class InvalidTokenTests {

        @Test
        @DisplayName("sends 401 when validateToken returns false")
        void invalidToken_sends401() throws Exception {
            when(request.getHeader("Authorization")).thenReturn("Bearer bad.token.here");
            when(jwtTokenProvider.validateToken("bad.token.here")).thenReturn(false);

            filter.doFilterInternal(request, response, filterChain);

            verify(response).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), anyString());
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("does not authenticate the user when token is invalid")
        void invalidToken_doesNotSetAuthentication() throws Exception {
            when(request.getHeader("Authorization")).thenReturn("Bearer bad.token.here");
            when(jwtTokenProvider.validateToken("bad.token.here")).thenReturn(false);

            filter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("sends 401 with descriptive message when token is invalid")
        void invalidToken_sends401WithMessage() throws Exception {
            when(request.getHeader("Authorization")).thenReturn("Bearer expired.jwt.token");
            when(jwtTokenProvider.validateToken("expired.jwt.token")).thenReturn(false);

            filter.doFilterInternal(request, response, filterChain);

            verify(response).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED),
                    eq("Invalid or expired JWT token"));
        }

        @Test
        @DisplayName("sends 401 when getClaims throws after validateToken returned true (defensive)")
        void getClaims_throws_sends401() throws Exception {
            when(request.getHeader("Authorization")).thenReturn("Bearer edge.case.token");
            when(jwtTokenProvider.validateToken("edge.case.token")).thenReturn(true);
            when(jwtTokenProvider.getClaims("edge.case.token"))
                    .thenThrow(new RuntimeException("Unexpected parse error"));
            when(request.getRequestURI()).thenReturn("/api/protected");

            filter.doFilterInternal(request, response, filterChain);

            verify(response).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), anyString());
            verify(filterChain, never()).doFilter(any(), any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Valid token
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("when Bearer token is valid")
    class ValidTokenTests {

        @Test
        @DisplayName("populates SecurityContext and forwards request to chain")
        void validToken_setsAuthenticationAndChains() throws Exception {
            io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
            when(claims.getSubject()).thenReturn("alice");
            when(claims.get("roles", java.util.List.class)).thenReturn(java.util.List.of("USER"));

            when(request.getHeader("Authorization")).thenReturn("Bearer valid.jwt.token");
            when(jwtTokenProvider.validateToken("valid.jwt.token")).thenReturn(true);
            when(jwtTokenProvider.getClaims("valid.jwt.token")).thenReturn(claims);

            filter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                    .isEqualTo("alice");
            verify(filterChain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("maps legacy USER claim to ROLE_PLAYER authority")
        void validToken_addsRolePrefix() throws Exception {
            io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
            when(claims.getSubject()).thenReturn("bob");
            when(claims.get("roles", java.util.List.class)).thenReturn(java.util.List.of("USER"));

            when(request.getHeader("Authorization")).thenReturn("Bearer valid.jwt");
            when(jwtTokenProvider.validateToken("valid.jwt")).thenReturn(true);
            when(jwtTokenProvider.getClaims("valid.jwt")).thenReturn(claims);

            filter.doFilterInternal(request, response, filterChain);

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth.getAuthorities())
                    .extracting("authority")
                    .containsExactly("ROLE_PLAYER");
        }

        @Test
        @DisplayName("does not double-prefix already prefixed roles")
        void validToken_doesNotDoublePrefixRole() throws Exception {
            io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
            when(claims.getSubject()).thenReturn("carol");
            when(claims.get("roles", java.util.List.class)).thenReturn(java.util.List.of("ROLE_ADMIN"));

            when(request.getHeader("Authorization")).thenReturn("Bearer valid.admin.jwt");
            when(jwtTokenProvider.validateToken("valid.admin.jwt")).thenReturn(true);
            when(jwtTokenProvider.getClaims("valid.admin.jwt")).thenReturn(claims);

            filter.doFilterInternal(request, response, filterChain);

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth.getAuthorities())
                    .extracting("authority")
                    .containsExactly("ROLE_PLATFORM_ADMIN");
        }

        @Test
        @DisplayName("handles null roles claim gracefully (empty authorities)")
        void validToken_nullRoles_grantsNoAuthorities() throws Exception {
            io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
            when(claims.getSubject()).thenReturn("dave");
            when(claims.get("roles", java.util.List.class)).thenReturn(null);

            when(request.getHeader("Authorization")).thenReturn("Bearer no.roles.jwt");
            when(jwtTokenProvider.validateToken("no.roles.jwt")).thenReturn(true);
            when(jwtTokenProvider.getClaims("no.roles.jwt")).thenReturn(claims);

            filter.doFilterInternal(request, response, filterChain);

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth.getAuthorities()).isEmpty();
        }
    }
}
