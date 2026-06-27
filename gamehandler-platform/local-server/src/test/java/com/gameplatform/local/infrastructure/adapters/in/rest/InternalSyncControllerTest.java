package com.gameplatform.local.infrastructure.adapters.in.rest;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gameplatform.local.domain.ports.in.SyncUsersUseCase;
import com.gameplatform.shared.dto.UserSyncDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class InternalSyncControllerTest {

    @Mock private SyncUsersUseCase syncUseCase;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new InternalSyncController(syncUseCase)).build();
    }

    @Test
    void syncReturns200AndDelegates() throws Exception {
        String body = "[{\"userId\":\"u1\",\"username\":\"a\",\"hashedPassword\":\"h\",\"roles\":[\"USER\"]}]";
        mvc.perform(put("/internal/users/sync")
                        .header("X-Internal-Api-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        verify(syncUseCase).syncUsers(anyList());
    }

    @Test
    void apiKeyHeaderIsNotEnforcedAtControllerLevel() throws Exception {
        // API key validation lives in InternalApiKeyFilter, not the controller. The controller accepts the request anyway.
        String body = "[]";
        mvc.perform(put("/internal/users/sync").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        verify(syncUseCase).syncUsers(List.of());
    }

    @Test
    void nullBodyTriggersHttpMessageNotReadable() throws Exception {
        mvc.perform(put("/internal/users/sync").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(syncUseCase);
    }
}
