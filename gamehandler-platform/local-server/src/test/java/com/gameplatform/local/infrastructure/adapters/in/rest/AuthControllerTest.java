package com.gameplatform.local.infrastructure.adapters.in.rest;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gameplatform.local.domain.exception.UserNotFoundException;
import com.gameplatform.local.domain.ports.in.AuthenticateLocalUserUseCase;
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

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private AuthenticateLocalUserUseCase useCase;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(useCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void loginReturns200AndToken() throws Exception {
        when(useCase.authenticate("alice", "pw"))
                .thenReturn(new LoginResponseDto("tok", "uid-1", Instant.parse("2026-02-01T10:00:00Z")));
        String body = "{\"username\":\"alice\",\"password\":\"pw\"}";
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("tok"))
                .andExpect(jsonPath("$.userId").value("uid-1"));
    }

    @Test
    void loginWithUnknownUserPropagatesAs500DueToMissingHandler() throws Exception {
        when(useCase.authenticate(any(), any())).thenThrow(new UserNotFoundException("nope"));
        String body = "{\"username\":\"x\",\"password\":\"y\"}";
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isInternalServerError());
    }
}
