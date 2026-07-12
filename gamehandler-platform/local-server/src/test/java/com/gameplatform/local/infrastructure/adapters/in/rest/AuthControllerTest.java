package com.gameplatform.local.infrastructure.adapters.in.rest;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gameplatform.local.domain.exception.UserAlreadyExistsException;
import com.gameplatform.local.domain.exception.UserNotFoundException;
import com.gameplatform.local.domain.model.LocalSignupUser;
import com.gameplatform.local.domain.ports.in.AuthenticateLocalUserUseCase;
import com.gameplatform.local.domain.ports.in.RegisterLocalUserUseCase;
import com.gameplatform.local.domain.ports.out.LocalAdminBuildingLocalRepository;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.LoginResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private AuthenticateLocalUserUseCase authenticateUseCase;
    @Mock private RegisterLocalUserUseCase registerUseCase;
    @Mock private UserRepository userRepository;
    @Mock private LocalAdminBuildingLocalRepository localAdminBuildingLocalRepository;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new AuthController(authenticateUseCase, registerUseCase, userRepository, localAdminBuildingLocalRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void loginReturns200AndToken() throws Exception {
        when(authenticateUseCase.authenticate("alice", "pw"))
                .thenReturn(new LoginResponseDto("tok", "uid-1", Instant.parse("2026-02-01T10:00:00Z")));
        String body = "{\"username\":\"alice\",\"password\":\"pw\"}";
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("tok"))
                .andExpect(jsonPath("$.userId").value("uid-1"));
    }

    @Test
    void loginWithUnknownUserReturns401() throws Exception {
        when(authenticateUseCase.authenticate(any(), any())).thenThrow(new UserNotFoundException("nope"));
        String body = "{\"username\":\"x\",\"password\":\"y\"}";
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signupReturns201AndUserDetails() throws Exception {
        LocalSignupUser user = new LocalSignupUser(
                new UserId("uid-1"), "alice", "hash", "alice@example.com",
                List.of("USER"), Instant.parse("2026-06-25T10:00:00Z"));
        when(registerUseCase.register("alice", "pw", "alice@example.com")).thenReturn(user);
        String body = "{\"username\":\"alice\",\"password\":\"pw\",\"email\":\"alice@example.com\"}";
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("uid-1"))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void signupWithExistingUserReturns409() throws Exception {
        when(registerUseCase.register(any(), any(), any()))
                .thenThrow(new UserAlreadyExistsException("Username already exists"));
        String body = "{\"username\":\"alice\",\"password\":\"pw\",\"email\":\"alice@example.com\"}";
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void signupWithInvalidInputReturns400() throws Exception {
        when(registerUseCase.register(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Username cannot be null or empty"));
        String body = "{\"username\":\"\",\"password\":\"pw\",\"email\":\"alice@example.com\"}";
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }
}
