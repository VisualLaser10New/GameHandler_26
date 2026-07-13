package com.gameplatform.local.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

@ExtendWith(MockitoExtension.class)
class InternalApiKeyFilterTest {

    private final String apiKey = "secret-key-123";
    private InternalApiKeyFilter filter;

    @Mock private FilterChain filterChain;

    @BeforeEach
    void setup() {
        filter = new InternalApiKeyFilter(apiKey, new MockEnvironment());
    }

    @Test
    void internalPathWithValidKeyContinues() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/internal/users/sync");
        request.addHeader("X-Internal-Api-Key", apiKey);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void internalPathWithMissingKeyReturns401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/internal/users/sync");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).contains("application/json");
        verifyNoInteractions(filterChain);
    }

    @Test
    void internalPathWithWrongKeyReturns401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/internal/users/sync");
        request.addHeader("X-Internal-Api-Key", "wrong");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(filterChain);
    }

    @Test
    void nonInternalPathSkipsCheck() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/games");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void internalPathPrefixMatch() throws ServletException, IOException {
        String[] paths = {"/internal/", "/internal/users/sync", "/internal/foo/bar"};
        for (String p : paths) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI(p);
            request.addHeader("X-Internal-Api-Key", apiKey);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, filterChain);
            verify(filterChain).doFilter(request, response);
        }
    }
}