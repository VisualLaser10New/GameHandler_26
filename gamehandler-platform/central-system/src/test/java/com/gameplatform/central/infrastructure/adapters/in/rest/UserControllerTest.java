package com.gameplatform.central.infrastructure.adapters.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.exception.UserAlreadyExistsException;
import com.gameplatform.central.domain.model.User;
import com.gameplatform.central.domain.ports.in.RegisterUserUseCase;
import com.gameplatform.shared.domain.model.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc slice tests for {@link UserController}.
 *
 * <p>Covers:
 * <ul>
 *   <li>400 Bad Request on blank/invalid input (Bean Validation via {@code @Valid})</li>
 *   <li>409 Conflict when {@link UserAlreadyExistsException} is thrown</li>
 *   <li>201 Created on successful registration</li>
 * </ul>
 * </p>
 */
@WebMvcTest(
        controllers = UserController.class,
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
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RegisterUserUseCase registerUserUseCase;


    // ──────────────────────────────────────────────────────────────────────────
    // 400 Bad Request — validation
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void registerUser_shouldReturn400_whenUsernameIsBlank() throws Exception {
        Map<String, String> body = Map.of(
                "username", "",
                "password", "securePass1",
                "email", "user@example.com"
        );

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void registerUser_shouldReturn400_whenPasswordIsBlank() throws Exception {
        Map<String, String> body = Map.of(
                "username", "alice",
                "password", "",
                "email", "user@example.com"
        );

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void registerUser_shouldReturn400_whenEmailIsInvalid() throws Exception {
        Map<String, String> body = Map.of(
                "username", "alice",
                "password", "securePass1",
                "email", "not-an-email"
        );

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void registerUser_shouldReturn400_whenPasswordTooShort() throws Exception {
        Map<String, String> body = Map.of(
                "username", "alice",
                "password", "short",
                "email", "user@example.com"
        );

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 409 Conflict — duplicate user
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void registerUser_shouldReturn409_whenUserAlreadyExists() throws Exception {
        when(registerUserUseCase.register(anyString(), anyString(), anyString()))
                .thenThrow(new UserAlreadyExistsException("Username or email already in use"));

        Map<String, String> body = Map.of(
                "username", "alice",
                "password", "securePass1",
                "email", "alice@example.com"
        );

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Username or email already in use"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 201 Created — happy path
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void registerUser_shouldReturn201_whenInputIsValid() throws Exception {
        User user = new User(
                new UserId("user-id-1"),
                "alice",
                "$2a$10$hashedPassword",
                "alice@example.com",
                List.of("USER"),
                Instant.now()
        );
        when(registerUserUseCase.register("alice", "securePass1", "alice@example.com")).thenReturn(user);

        Map<String, String> body = Map.of(
                "username", "alice",
                "password", "securePass1",
                "email", "alice@example.com"
        );

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("alice"));
    }
}
