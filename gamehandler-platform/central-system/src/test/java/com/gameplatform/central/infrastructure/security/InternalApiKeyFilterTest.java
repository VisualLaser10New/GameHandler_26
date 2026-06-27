package com.gameplatform.central.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link InternalApiKeyFilter}.
 *
 * <ul>
 *   <li>Blank / null API key at startup → {@link IllegalStateException}.</li>
 *   <li>Missing {@code X-Internal-Api-Key} header on {@code /internal/**}
 *       → {@code 403 Forbidden}, chain halted.</li>
 *   <li>Blank header value → {@code 403 Forbidden}, chain halted.</li>
 *   <li>Wrong API key → {@code 403 Forbidden}, chain halted.</li>
 *   <li>Correct API key → chain continues.</li>
 *   <li>Non-internal paths are never checked for the key.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InternalApiKeyFilter")
class InternalApiKeyFilterTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private static final String VALID_KEY = "super-secret-key";

    /** Builds a filter with the given key and calls @PostConstruct manually. */
    private InternalApiKeyFilter buildFilter(String key) {
        InternalApiKeyFilter filter = new InternalApiKeyFilter(key);
        filter.validateConfiguration();
        return filter;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // @PostConstruct validation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("startup validation (@PostConstruct)")
    class StartupValidationTests {

        @Test
        @DisplayName("throws IllegalStateException when api-key is null")
        void nullApiKey_throwsIllegalStateException() {
            InternalApiKeyFilter filter = new InternalApiKeyFilter(null);
            assertThatThrownBy(filter::validateConfiguration)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("internal.api-key");
        }

        @Test
        @DisplayName("throws IllegalStateException when api-key is empty string")
        void emptyApiKey_throwsIllegalStateException() {
            InternalApiKeyFilter filter = new InternalApiKeyFilter("");
            assertThatThrownBy(filter::validateConfiguration)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("internal.api-key");
        }

        @Test
        @DisplayName("throws IllegalStateException when api-key is whitespace only")
        void blankApiKey_throwsIllegalStateException() {
            InternalApiKeyFilter filter = new InternalApiKeyFilter("   ");
            assertThatThrownBy(filter::validateConfiguration)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("internal.api-key");
        }

        @Test
        @DisplayName("does not throw when api-key is a non-blank string")
        void validApiKey_doesNotThrow() {
            // Should complete without exception
            InternalApiKeyFilter filter = new InternalApiKeyFilter(VALID_KEY);
            filter.validateConfiguration();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // /internal/** path — missing or blank header
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("internal path — missing or blank header")
    class MissingHeaderTests {

        @Test
        @DisplayName("returns 403 when X-Internal-Api-Key header is absent")
        void missingHeader_returns403() throws Exception {
            InternalApiKeyFilter filter = buildFilter(VALID_KEY);
            when(request.getRequestURI()).thenReturn("/internal/sync");
            when(request.getHeader("X-Internal-Api-Key")).thenReturn(null);

            filter.doFilterInternal(request, response, filterChain);

            verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("returns 403 when X-Internal-Api-Key header is an empty string")
        void emptyHeader_returns403() throws Exception {
            InternalApiKeyFilter filter = buildFilter(VALID_KEY);
            when(request.getRequestURI()).thenReturn("/internal/sync");
            when(request.getHeader("X-Internal-Api-Key")).thenReturn("");

            filter.doFilterInternal(request, response, filterChain);

            verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
            verify(filterChain, never()).doFilter(any(), any());
        }

        @ParameterizedTest(name = "blank header [{0}] → 403")
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("returns 403 when X-Internal-Api-Key header is whitespace-only")
        void blankHeader_returns403(String blankValue) throws Exception {
            InternalApiKeyFilter filter = buildFilter(VALID_KEY);
            when(request.getRequestURI()).thenReturn("/internal/sync");
            when(request.getHeader("X-Internal-Api-Key")).thenReturn(blankValue);

            filter.doFilterInternal(request, response, filterChain);

            verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
            verify(filterChain, never()).doFilter(any(), any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // /internal/** path — wrong key
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("internal path — wrong key")
    class WrongKeyTests {

        @Test
        @DisplayName("returns 403 when the provided key does not match")
        void wrongKey_returns403() throws Exception {
            InternalApiKeyFilter filter = buildFilter(VALID_KEY);
            when(request.getRequestURI()).thenReturn("/internal/sync");
            when(request.getHeader("X-Internal-Api-Key")).thenReturn("wrong-key");

            filter.doFilterInternal(request, response, filterChain);

            verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("returns 403 for a key that is a prefix of the valid key")
        void prefixKey_returns403() throws Exception {
            InternalApiKeyFilter filter = buildFilter(VALID_KEY);
            when(request.getRequestURI()).thenReturn("/internal/sync");
            when(request.getHeader("X-Internal-Api-Key")).thenReturn(VALID_KEY.substring(0, VALID_KEY.length() - 1));

            filter.doFilterInternal(request, response, filterChain);

            verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // /internal/** path — valid key
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("internal path — valid key")
    class ValidKeyTests {

        @Test
        @DisplayName("forwards request to chain when correct key is supplied")
        void validKey_chainsThrough() throws Exception {
            InternalApiKeyFilter filter = buildFilter(VALID_KEY);
            when(request.getRequestURI()).thenReturn("/internal/sync");
            when(request.getHeader("X-Internal-Api-Key")).thenReturn(VALID_KEY);

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("forwards request for nested internal paths")
        void validKey_nestedPath_chainsThrough() throws Exception {
            InternalApiKeyFilter filter = buildFilter(VALID_KEY);
            when(request.getRequestURI()).thenReturn("/internal/users/sync");
            when(request.getHeader("X-Internal-Api-Key")).thenReturn(VALID_KEY);

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Non-internal paths — key is never checked
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("non-internal path — key not required")
    class NonInternalPathTests {

        @Test
        @DisplayName("always forwards /api/** requests regardless of key header")
        void publicPath_chainsThrough_withoutHeader() throws Exception {
            InternalApiKeyFilter filter = buildFilter(VALID_KEY);
            when(request.getRequestURI()).thenReturn("/api/auth/login");

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("does not inspect the key header for non-internal paths")
        void publicPath_headerNotInspected() throws Exception {
            InternalApiKeyFilter filter = buildFilter(VALID_KEY);
            when(request.getRequestURI()).thenReturn("/api/users");

            filter.doFilterInternal(request, response, filterChain);

            // Header must never be read for public paths
            verify(request, never()).getHeader("X-Internal-Api-Key");
        }
    }
}
