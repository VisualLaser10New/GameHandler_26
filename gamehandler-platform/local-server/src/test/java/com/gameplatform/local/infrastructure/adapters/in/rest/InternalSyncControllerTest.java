package com.gameplatform.local.infrastructure.adapters.in.rest;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gameplatform.local.domain.ports.in.SyncUsersUseCase;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.dto.UserSyncAckDto;
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
    @Mock private UserRepository userRepository;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new InternalSyncController(syncUseCase, userRepository)).build();
    }

    @Test
    void syncReturns200WithOneAckPerInputUser() throws Exception {
        String body = "[{\"userId\":\"u1\",\"username\":\"a\",\"hashedPassword\":\"h\",\"roles\":[\"USER\"]}]";
        when(syncUseCase.syncUsers(anyList()))
                .thenReturn(List.of(new UserSyncAckDto("u1", true, null)));

        mvc.perform(put("/internal/users/sync")
                        .header("X-Internal-Api-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("u1"))
                .andExpect(jsonPath("$[0].applied").value(true))
                .andExpect(jsonPath("$[0].reason").doesNotExist());
        verify(syncUseCase).syncUsers(anyList());
    }

    @Test
    void syncReturnsAcksForAllUsersAndPoisonDoesNotAbort() throws Exception {
        // Two users: one happy, one poison — the batch returns BOTH acks (200, not 5xx).
        String body = "[{\"userId\":\"u1\",\"username\":\"a\",\"hashedPassword\":\"h\",\"roles\":[\"USER\"]},"
                + "{\"userId\":\"u2\",\"username\":\"\",\"hashedPassword\":\"h\",\"roles\":[\"USER\"]}]";
        when(syncUseCase.syncUsers(anyList())).thenReturn(List.of(
                new UserSyncAckDto("u1", true, null),
                new UserSyncAckDto("u2", false, "VALIDATION_ERROR: Username cannot be null or empty")));

        mvc.perform(put("/internal/users/sync")
                        .header("X-Internal-Api-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userId").value("u1"))
                .andExpect(jsonPath("$[0].applied").value(true))
                .andExpect(jsonPath("$[1].userId").value("u2"))
                .andExpect(jsonPath("$[1].applied").value(false))
                .andExpect(jsonPath("$[1].reason").value(
                        org.hamcrest.Matchers.startsWith("VALIDATION_ERROR")));
        verify(syncUseCase).syncUsers(anyList());
    }

    @Test
    void apiKeyHeaderIsNotEnforcedAtControllerLevel() throws Exception {
        // API key validation lives in InternalApiKeyFilter, not the controller. The controller accepts the request anyway.
        String body = "[]";
        when(syncUseCase.syncUsers(List.of())).thenReturn(List.of());

        mvc.perform(put("/internal/users/sync").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        verify(syncUseCase).syncUsers(List.of());
    }

    @Test
    void nullBodyTriggersHttpMessageNotReadable() throws Exception {
        mvc.perform(put("/internal/users/sync").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(syncUseCase);
    }
}
