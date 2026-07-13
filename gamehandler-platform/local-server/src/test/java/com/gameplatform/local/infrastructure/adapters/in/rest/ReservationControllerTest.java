package com.gameplatform.local.infrastructure.adapters.in.rest;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gameplatform.local.domain.exception.ReservationExpiredException;
import com.gameplatform.local.domain.exception.ReservationNotFoundException;
import com.gameplatform.local.domain.model.Reservation;
import com.gameplatform.local.domain.ports.in.CancelReservationUseCase;
import com.gameplatform.local.domain.ports.in.CreateReservationUseCase;
import com.gameplatform.local.domain.ports.in.GetReservationsUseCase;
import com.gameplatform.local.infrastructure.security.CurrentUserService;
import com.gameplatform.shared.domain.model.*;
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
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class ReservationControllerTest {

    @Mock private CreateReservationUseCase createUseCase;
    @Mock private CancelReservationUseCase cancelUseCase;
    @Mock private GetReservationsUseCase getUseCase;
    @Mock private CurrentUserService currentUserService;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new ReservationController(createUseCase, cancelUseCase, getUseCase, currentUserService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private Reservation sample() {
        return new Reservation(new ReservationId("r1"), new GameId("g1"), new UserId("u1"),
                ReservationStatus.PENDING, Instant.parse("2026-02-01T10:00:00Z"),
                Instant.parse("2026-02-01T11:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void createReturns201AndDto() throws Exception {
        when(createUseCase.create(any(), any(), any(), any())).thenReturn(sample());
        String body = "{\"gameId\":\"g1\",\"userId\":\"u1\",\"startTime\":\"2026-02-01T10:00:00Z\",\"endTime\":\"2026-02-01T11:00:00Z\"}";
        mvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("r1"))
                .andExpect(jsonPath("$.gameId").value("g1"))
                .andExpect(jsonPath("$.userId").value("u1"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createWithBlankGameIdPropagatesIllegalArgFromRecordCtor() throws Exception {
        // GameId(null/blank) throws IllegalArgumentException inside the controller -> 400 due to global exception handler
        String body = "{\"gameId\":\"\",\"userId\":\"u1\",\"startTime\":\"2026-02-01T10:00:00Z\",\"endTime\":\"2026-02-01T11:00:00Z\"}";
        mvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(createUseCase);
    }

    @Test
    void cancelReturns204() throws Exception {
        mvc.perform(delete("/api/reservations/r1"))
                .andExpect(status().isNoContent());
        verify(cancelUseCase).cancel(new ReservationId("r1"));
    }

    @Test
    void cancelWhenNotFoundPropagatesAs500DueToMissingHandler() throws Exception {
        doThrow(new ReservationNotFoundException("not found")).when(cancelUseCase).cancel(any());
        mvc.perform(delete("/api/reservations/r1"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void cancelWhenExpiredPropagatesAs500DueToMissingHandler() throws Exception {
        doThrow(new ReservationExpiredException("expired")).when(cancelUseCase).cancel(any());
        mvc.perform(delete("/api/reservations/r1"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getByUserReturnsList() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(new UserId("u1")));
        when(getUseCase.getByUser(new UserId("u1"))).thenReturn(List.of(sample()));
        mvc.perform(get("/api/reservations").param("userId", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("r1"));
    }

    @Test
    void getByUserEmptyReturnsEmptyArray() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(new UserId("u1")));
        when(getUseCase.getByUser(any())).thenReturn(List.of());
        mvc.perform(get("/api/reservations").param("userId", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getByUser_otherUser_returns403() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(new UserId("me")));
        mvc.perform(get("/api/reservations").param("userId", "other"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(getUseCase);
    }

    @Test
    void getByUser_noPrincipal_returns401() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.empty());
        mvc.perform(get("/api/reservations").param("userId", "u1"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(getUseCase);
    }
}
