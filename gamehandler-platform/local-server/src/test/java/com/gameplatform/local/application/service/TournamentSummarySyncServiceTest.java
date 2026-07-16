package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gameplatform.local.domain.model.TournamentSummaryLocal;
import com.gameplatform.local.domain.ports.out.AdminRequestRepository;
import com.gameplatform.local.domain.ports.out.TournamentSummaryLocalRepository;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.dto.TournamentSummaryEventDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Pure-Mockito unit tests for {@link TournamentSummarySyncService}, covering
 * the FASE 7-A2 idempotent upsert of replicated
 * {@code TOURNAMENT_SUMMARY_UPSERTED} events into the
 * {@code tournaments_summary_local} table. Mirrors the
 * {@code TournamentMatchLocalSyncServiceTest} convention.
 *
 * <p>Coverage:
 * <ul>
 *   <li>upsert on a regular event;</li>
 *   <li>idempotency on re-delivery (save is called twice with equal domain objects);</li>
 *   <li>tombstone {@code deleted==true} → {@code deleteById} (not {@code save});</li>
 *   <li>safe re-delivery of a tombstone after upsert (deleteById twice — second is no-op);</li>
 *   <li>null / empty list / null event / unknown eventType / blank tournamentId skips.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TournamentSummarySyncServiceTest {

    private static final Instant UPDATED_AT = Instant.parse("2026-07-12T10:00:00Z");
    private static final Instant STARTS_AT = Instant.parse("2026-08-01T10:00:00Z");

    @Mock TournamentSummaryLocalRepository tournamentSummaryLocalRepository;
    @Mock AdminRequestRepository adminRequestRepository;
    @InjectMocks TournamentSummarySyncService service;

    private TournamentSummaryEventDto summaryDto(String tournamentId, boolean deleted) {
        return new TournamentSummaryEventDto(
                "evt-" + tournamentId,
                "TOURNAMENT_SUMMARY_UPSERTED",
                tournamentId,
                "Test Cup",
                GameType.CHESS,
                false,
                1,
                TournamentStatus.DRAFT,
                STARTS_AT,
                null,
                List.of("b-1", "b-2"),
                0,
                UPDATED_AT,
                deleted,
                null
        );
    }

    @Test
    void applyEvents_upsertsSingleSummaryEvent() {
        TournamentSummaryEventDto dto = summaryDto("t-1", false);

        service.applyEvents(List.of(dto));

        ArgumentCaptor<TournamentSummaryLocal> captor = ArgumentCaptor.forClass(TournamentSummaryLocal.class);
        verify(tournamentSummaryLocalRepository).save(captor.capture());
        TournamentSummaryLocal saved = captor.getValue();
        assertEquals(new TournamentId("t-1"), saved.getTournamentId());
        assertEquals("Test Cup", saved.getName());
        assertEquals(GameType.CHESS, saved.getGameType());
        assertFalse(saved.isTeamBased());
        assertEquals(1, saved.getTeamSize());
        assertEquals(TournamentStatus.DRAFT, saved.getStatus());
        assertEquals(STARTS_AT, saved.getStartsAt());
        assertNull(saved.getEndsAt());
        assertEquals(List.of("b-1", "b-2"), saved.getBuildingIds());
        assertEquals(0, saved.getParticipantsCount());
        assertFalse(saved.isDeleted());
        assertEquals(UPDATED_AT, saved.getUpdatedAt());
        verify(tournamentSummaryLocalRepository, never()).deleteById(any());
    }

    @Test
    void applyEvents_isIdempotentOnRedelivery_upsert() {
        TournamentSummaryEventDto dto = summaryDto("t-1", false);
        when(tournamentSummaryLocalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.applyEvents(List.of(dto));
        service.applyEvents(List.of(dto));

        ArgumentCaptor<TournamentSummaryLocal> captor = ArgumentCaptor.forClass(TournamentSummaryLocal.class);
        verify(tournamentSummaryLocalRepository, times(2)).save(captor.capture());
        List<TournamentSummaryLocal> saved = captor.getAllValues();
        assertEquals(2, saved.size());
        // Upsert-by-PK: both domain objects are equal (identity by tournamentId).
        assertEquals(saved.get(0), saved.get(1));
        assertEquals(new TournamentId("t-1"), saved.get(0).getTournamentId());
    }

    @Test
    void applyEvents_tombstoneDeletedTrue_callsDeleteById_andNeverSave() {
        TournamentSummaryEventDto dto = summaryDto("t-1", true);

        service.applyEvents(List.of(dto));

        verify(tournamentSummaryLocalRepository).deleteById(new TournamentId("t-1"));
        verify(tournamentSummaryLocalRepository, never()).save(any());
    }

    @Test
    void applyEvents_tombstoneRedeliveryIsSafe_deleteByIdTwiceIsNoOp() {
        TournamentSummaryEventDto dto = summaryDto("t-1", true);

        service.applyEvents(List.of(dto));
        service.applyEvents(List.of(dto));

        // deleteById is idempotent — second call is a no-op on the (already missing) PK
        verify(tournamentSummaryLocalRepository, times(2)).deleteById(new TournamentId("t-1"));
        verify(tournamentSummaryLocalRepository, never()).save(any());
    }

    @Test
    void applyEvents_upsertAfterTombstone_restoresProjection() {
        // Simulate a delete followed by a re-create with the same tournamentId
        TournamentSummaryEventDto tombstone = summaryDto("t-1", true);
        TournamentSummaryEventDto upsert = summaryDto("t-1", false);

        service.applyEvents(List.of(tombstone, upsert));

        // deleteById first, then save (re-creates the projection)
        verify(tournamentSummaryLocalRepository).deleteById(new TournamentId("t-1"));
        verify(tournamentSummaryLocalRepository).save(any(TournamentSummaryLocal.class));
    }

    @Test
    void applyEvents_skipsNullEvents() {
        service.applyEvents(Arrays.asList((TournamentSummaryEventDto) null));

        verifyNoInteractions(tournamentSummaryLocalRepository);
    }

    @Test
    void applyEvents_handlesEmptyList() {
        service.applyEvents(List.of());

        verifyNoInteractions(tournamentSummaryLocalRepository);
    }

    @Test
    void applyEvents_nullListIsNoOp() {
        service.applyEvents(null);

        verifyNoInteractions(tournamentSummaryLocalRepository);
    }

    @Test
    void applyEvents_unknownEventTypeLogsAndSkips() {
        TournamentSummaryEventDto dto = new TournamentSummaryEventDto(
                "evt-x", "FOO", "t-1", "Test Cup", GameType.CHESS, false, 1,
                TournamentStatus.DRAFT, STARTS_AT, null, List.of("b-1"), 0, UPDATED_AT, false, null);

        service.applyEvents(List.of(dto));

        verify(tournamentSummaryLocalRepository, never()).save(any());
        verify(tournamentSummaryLocalRepository, never()).deleteById(any());
    }

    @Test
    void applyEvents_blankTournamentIdLogsAndSkips() {
        TournamentSummaryEventDto dto = new TournamentSummaryEventDto(
                "evt-x", "TOURNAMENT_SUMMARY_UPSERTED", " ", "Test Cup", GameType.CHESS, false, 1,
                TournamentStatus.DRAFT, STARTS_AT, null, List.of("b-1"), 0, UPDATED_AT, false, null);

        service.applyEvents(List.of(dto));

        verify(tournamentSummaryLocalRepository, never()).save(any());
        verify(tournamentSummaryLocalRepository, never()).deleteById(any());
    }

    @Test
    void applyEvents_originatingRequestIdTriggersMarkCompleted() {
        TournamentSummaryEventDto dto = new TournamentSummaryEventDto(
                "evt-req", "TOURNAMENT_SUMMARY_UPSERTED", "t-1", "Test Cup", GameType.CHESS, false, 1,
                TournamentStatus.DRAFT, STARTS_AT, null, List.of("b-1"), 0, UPDATED_AT, false,
                "request-uuid-123");

        service.applyEvents(List.of(dto));

        ArgumentCaptor<TournamentSummaryLocal> captor = ArgumentCaptor.forClass(TournamentSummaryLocal.class);
        verify(tournamentSummaryLocalRepository).save(captor.capture());
        // The projection row does NOT carry originatingRequestId (it is an envelope field, not data)
        assertEquals(new TournamentId("t-1"), captor.getValue().getTournamentId());
        // The non-null originatingRequestId triggers the admin-request closure (markCompleted).
        verify(adminRequestRepository).markCompleted(eq("request-uuid-123"), anyString(), any());
    }

    @Test
    void applyEvents_originatingRequestIdWithError_triggersMarkFailed_withReadableReason() {
        // BUG-CANCEL-PENDING closure (Local side): a non-null errorMessage on a
        // TOURNAMENT_SUMMARY_UPSERTED return event signals Central-rejected
        // admin-request (e.g. cancel refused because the tournament is COMPLETED).
        // The matching admin_requests_local row MUST be transitioned to FAILED
        // (with result_data={"reason":"…","applied":false,"tournamentId":"…"}) so
        // the AdminRequestsView card surfaces "reason: Cannot cancel from
        // status COMPLETED" within seconds, not a 30-min TIMEOUT.
        TournamentSummaryEventDto dto = new TournamentSummaryEventDto(
                "evt-fail", "TOURNAMENT_SUMMARY_UPSERTED", "t-completed", "Active Cup", GameType.CHESS, false, 1,
                TournamentStatus.COMPLETED, STARTS_AT, null, List.of("b-1"), 3, UPDATED_AT, false,
                "request-fail-1", "Cannot cancel from status COMPLETED");

        service.applyEvents(List.of(dto));

        // The projection upsert still runs (the snapshot reflects the unchanged Central state).
        ArgumentCaptor<TournamentSummaryLocal> captor = ArgumentCaptor.forClass(TournamentSummaryLocal.class);
        verify(tournamentSummaryLocalRepository).save(captor.capture());
        assertEquals(new TournamentId("t-completed"), captor.getValue().getTournamentId());
        assertEquals(TournamentStatus.COMPLETED, captor.getValue().getStatus());

        // The markFailed hook must fire WITH the readable reason in result_data.
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(adminRequestRepository).markFailed(eq("request-fail-1"), reasonCaptor.capture(), any());
        String resultData = reasonCaptor.getValue();
        assertTrue(resultData.contains("\"reason\":\"Cannot cancel from status COMPLETED\""),
                "result_data MUST embed the readable reason: " + resultData);
        assertTrue(resultData.contains("\"applied\":false"), "result_data MUST flip applied=false: " + resultData);
        assertTrue(resultData.contains("\"tournamentId\":\"t-completed\""),
                "result_data MUST echo the tournamentId: " + resultData);
        // markCompleted must NOT be called on the failed branch.
        verify(adminRequestRepository, never()).markCompleted(anyString(), anyString(), any());
    }

    @Test
    void applyEvents_originatingRequestIdWithErrorAndTombstone_triggersMarkFailed_noProjectionDeleteOnly() {
        // Tournament-not-found variant: the Central emits a tombstone
        // (deleted=true) + errorMessage so the Local deletes any stale projection
        // row (no-op when absent) AND closes the admin-request as FAILED.
        TournamentSummaryEventDto dto = new TournamentSummaryEventDto(
                "evt-tomb-fail", "TOURNAMENT_SUMMARY_UPSERTED", "t-missing", "(not-found)", null, false, 1,
                TournamentStatus.DRAFT, STARTS_AT, null, List.of(), 0, UPDATED_AT, true,
                "request-fail-404", "Tournament not found: t-missing");

        service.applyEvents(List.of(dto));

        // deleteById is invoked on the tombstone branch (idempotent on missing PK).
        verify(tournamentSummaryLocalRepository).deleteById(new TournamentId("t-missing"));
        verify(tournamentSummaryLocalRepository, never()).save(any());

        // markFailed is still invoked with the readable "Tournament not found" reason.
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(adminRequestRepository).markFailed(eq("request-fail-404"), reasonCaptor.capture(), any());
        String resultData = reasonCaptor.getValue();
        assertTrue(resultData.contains("\"reason\":\"Tournament not found: t-missing\""),
                "result_data MUST embed the not-found reason: " + resultData);
        verify(adminRequestRepository, never()).markCompleted(anyString(), anyString(), any());
    }
}
