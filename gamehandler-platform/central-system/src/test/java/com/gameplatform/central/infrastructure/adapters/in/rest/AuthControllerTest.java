package com.gameplatform.central.infrastructure.adapters.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.exception.InvalidCredentialsException;
import com.gameplatform.central.domain.exception.RateLimitExceededException;
import com.gameplatform.central.domain.ports.in.AuthenticateUserUseCase;
import com.gameplatform.shared.dto.LoginResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AuthController.class,
        excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
                type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
                classes = {
                        com.gameplatform.central.infrastructure.config.SecurityConfig.class,
                        com.gameplatform.central.infrastructure.security.JwtAuthenticationFilter.class,
                        com.gameplatform.central.infrastructure.security.InternalApiKeyFilter.class
                }
        )
)
@Import({GlobalExceptionHandler.class, TestSecurityConfig.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthenticateUserUseCase authenticateUserUseCase;

    @Test
    void login_shouldReturn200_whenCredentialsAreValid() throws Exception {
        LoginResponseDto response = new LoginResponseDto("dummy-jwt-token", "alice", java.time.Instant.now().plusSeconds(3600));
        when(authenticateUserUseCase.authenticate("alice", "password123")).thenReturn(response);

        Map<String, String> body = Map.of(
                "username", "alice",
                "password", "password123"
        );

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("dummy-jwt-token"));
    }

    @Test
    void login_shouldReturn400_whenUsernameIsBlank() throws Exception {
        Map<String, String> body = Map.of(
                "username", "",
                "password", "password123"
        );

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void login_shouldReturn401_whenCredentialsAreInvalid() throws Exception {
        when(authenticateUserUseCase.authenticate(anyString(), anyString()))
                .thenThrow(new InvalidCredentialsException("Invalid credentials"));

        Map<String, String> body = Map.of(
                "username", "alice",
                "password", "wrongpassword"
        );

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    @Test
    void login_shouldReturn429_whenRateLimitExceeded() throws Exception {
        when(authenticateUserUseCase.authenticate(anyString(), anyString()))
                .thenThrow(new RateLimitExceededException("Too many failed attempts"));

        Map<String, String> body = Map.of(
                "username", "alice",
                "password", "password123"
        );

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Too many failed attempts"));
    }
}
