package com.gameplatform.central.infrastructure.adapters.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.ports.in.ListTournamentParticipantsUseCase;
import com.gameplatform.central.domain.ports.in.RegisterTournamentParticipantUseCase;
import com.gameplatform.central.domain.ports.in.UnregisterTournamentParticipantUseCase;
import com.gameplatform.central.infrastructure.security.CurrentUserService;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.RegisterTournamentParticipantDto;
import com.gameplatform.shared.dto.TournamentParticipantDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TournamentRegistrationControllerTest {

    @Mock
    private RegisterTournamentParticipantUseCase registerUseCase;

    @Mock
    private UnregisterTournamentParticipantUseCase unregisterUseCase;

    @Mock
    private ListTournamentParticipantsUseCase listUseCase;

    @Mock
    private CurrentUserService currentUserService;

    private TournamentRegistrationController controller;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        controller = new TournamentRegistrationController(registerUseCase, unregisterUseCase,
                listUseCase, currentUserService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void postRegister_individual_returns200() throws Exception {
        RegisterTournamentParticipantDto body = new RegisterTournamentParticipantDto(null, null);
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(new UserId("u-1")));
        when(registerUseCase.register(any(), any(), any(), any()))
                .thenReturn(new TournamentParticipantDto("u-1", false, "alice"));

        mockMvc.perform(post("/api/tournaments/t-1/participants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantId").value("u-1"))
                .andExpect(jsonPath("$.isTeam").value(false));
    }

    @Test
    void postRegister_team_returns200() throws Exception {
        RegisterTournamentParticipantDto body = new RegisterTournamentParticipantDto("MyTeam", List.of("captain", "member2"));
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(new UserId("captain")));
        when(registerUseCase.register(any(), any(), eq("MyTeam"), any()))
                .thenReturn(new TournamentParticipantDto("team-uuid", true, "MyTeam"));

        mockMvc.perform(post("/api/tournaments/t-1/participants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isTeam").value(true))
                .andExpect(jsonPath("$.displayName").value("MyTeam"));
    }

    @Test
    void deleteUnregister_returns204() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(new UserId("u-1")));

        mockMvc.perform(delete("/api/tournaments/t-1/participants"))
                .andExpect(status().isNoContent());

        verify(unregisterUseCase).unregister(any(), any());
    }

    @Test
    void getList_returns200() throws Exception {
        when(listUseCase.listParticipants(new TournamentId("t-1")))
                .thenReturn(List.of(new TournamentParticipantDto("u-1", false, "alice")));

        mockMvc.perform(get("/api/tournaments/t-1/participants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
