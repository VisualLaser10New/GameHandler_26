package com.gameplatform.local.infrastructure.adapters.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gameplatform.local.domain.ports.in.AuthenticateLocalUserUseCase;
import com.gameplatform.local.domain.ports.in.RegisterLocalUserUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerSignupEdgeCaseTest {

    @Mock
    private AuthenticateLocalUserUseCase authenticateUseCase;

    @Mock
    private RegisterLocalUserUseCase registerUseCase;

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(authenticateUseCase, registerUseCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("signup with null fields is mapped to 400 Bad Request")
    void signupWithNullFieldsReturns400() throws Exception {
        when(registerUseCase.register(null, null, null)).thenThrow(new IllegalArgumentException("null"));

        mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":null,\"password\":null,\"email\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("signup with missing email field is mapped to 400 Bad Request")
    void signupWithMissingEmailReturns400() throws Exception {
        when(registerUseCase.register("a", "b", null)).thenThrow(new IllegalArgumentException("null email"));

        mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"a\",\"password\":\"b\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("signup when the service throws a generic RuntimeException is mapped to 500 (outbox/serialization failure)")
    void signupWhenServiceThrowsRuntimeExceptionReturns500() throws Exception {
        when(registerUseCase.register(any(), any(), any())).thenThrow(new RuntimeException("outbox failure"));

        mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"a\",\"password\":\"b\",\"email\":\"c@d.com\"}"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("signup when input validation fails is mapped to 400, not 409")
    void signupWithBlankInputReturns400NotConflict() throws Exception {
        when(registerUseCase.register(any(), any(), any())).thenThrow(new IllegalArgumentException("blank"));

        mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\",\"email\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
