package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.TournamentSummaryLocal;
import com.gameplatform.local.domain.ports.out.AdminRequestRepository;
import com.gameplatform.local.domain.ports.out.TournamentSummaryLocalRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.dto.TournamentSummaryEventDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Riceve eventi {@code TOURNAMENT_SUMMARY_UPSERTED} replicati dal Central
 * tramite outbox e li applica idempotentemente alla tabella
 * {@code tournaments_summary_local}. L'idempotenza e' garantita dalla
 * chiave primaria {@code tournamentId} (upsert per save, deleteById per
 * i tombstone).
 *
 * <p>Per ogni evento nel batch:
 * <ul>
 *   <li>{@code deleted == true} (tombstone) → la riga di proiezione viene
 *       rimossa fisicamente. Sicuro alla ri-consegna: deleteById su PK
 *       assente e' un no-op.</li>
 *   <li>altrimenti → viene costruito uno snapshot fresco
 *       {@link TournamentSummaryLocal} dal DTO e salvato (upsert per PK).</li>
 * </ul>
 *
 * <p>Quando {@code originatingRequestId != null} (evento di ritorno dal
 * Central), la riga {@code admin_requests_local} corrispondente viene
 * transizionata a COMPLETED o FAILED in base alla presenza di un
 * {@code errorMessage} (BUG-CANCEL-PENDING).</p>
 *
 * @see TournamentSummaryLocalRepository
 * @see AdminRequestRepository
 * @see TournamentMatchLocalSyncService
 * @see GameDefinitionSyncService
 */
@Service
@Transactional
public class TournamentSummarySyncService {

    private static final Logger log = LoggerFactory.getLogger(TournamentSummarySyncService.class);

    static final String EVENT_TOURNAMENT_SUMMARY_UPSERTED = "TOURNAMENT_SUMMARY_UPSERTED";

    private final TournamentSummaryLocalRepository tournamentSummaryLocalRepository;
    private final AdminRequestRepository adminRequestRepository;

    public TournamentSummarySyncService(TournamentSummaryLocalRepository tournamentSummaryLocalRepository,
                                         AdminRequestRepository adminRequestRepository) {
        this.tournamentSummaryLocalRepository = tournamentSummaryLocalRepository;
        this.adminRequestRepository = adminRequestRepository;
    }

    /**
     * Applica una lista di eventi di riepilogo torneo alla tabella locale.
     * Per ogni evento TOURNAMENT_SUMMARY_UPSERTED, upserta o elimina la
     * riga in base al flag deleted. Se l'evento trasporta un
     * originatingRequestId, la richiesta admin corrispondente viene
     * marcata come COMPLETED o FAILED in base alla presenza di
     * errorMessage.
     *
     * @param events la lista di eventi da applicare (puo' essere null)
     */
    public void applyEvents(List<TournamentSummaryEventDto> events) {
        if (events == null) {
            return;
        }
        for (TournamentSummaryEventDto event : events) {
            if (event == null) {
                continue;
            }
            String eventType = event.eventType();
            if (!EVENT_TOURNAMENT_SUMMARY_UPSERTED.equals(eventType)) {
                log.warn("Unknown tournament-summary event type: {}", eventType);
                continue;
            }
            if (event.tournamentId() == null || event.tournamentId().isBlank()) {
                log.warn("Tournament-summary event with blank tournamentId skipped");
                continue;
            }
            TournamentId tournamentId = new TournamentId(event.tournamentId());
            boolean tombstone = event.deleted();
            if (tombstone) {
                log.info("Tournament-summary tombstone event [{}] for tournament {} — deleting projection row",
                        event.eventId(), tournamentId.value());
                tournamentSummaryLocalRepository.deleteById(tournamentId);
            } else {
                TournamentSummaryLocal summary = new TournamentSummaryLocal(
                        tournamentId,
                        event.name(),
                        event.gameType(),
                        event.teamBased(),
                        event.teamSize(),
                        event.status(),
                        event.startsAt(),
                        event.endsAt(),
                        event.buildingIds(),
                        event.participantsCount(),
                        false,
                        event.updatedAt() != null ? event.updatedAt() : java.time.Instant.now()
                );
                tournamentSummaryLocalRepository.save(summary);
                log.info("Tournament-summary event [{}] upserted for tournament {} (status={}, participants={})",
                        event.eventId(), tournamentId.value(), summary.getStatus(), summary.getParticipantsCount());
            }
            markClosedIfRequested(event.eventId(), event.originatingRequestId(), tombstone,
                    event.tournamentId(), event.errorMessage());
        }
    }

    /**
     * Closes the matching {@code admin_requests_local} row when the
     * replicated summary event is a Central return-event
     * ({@code originatingRequestId != null}).
     *
     * <p>Branches on {@code errorMessage}:
     * <ul>
     *   <li>{@code errorMessage == null} → success outcome →
     *       {@link AdminRequestRepository#markCompleted} with
     *       {@code result_data = \{"deleted":…,"applied":true,"tournamentId":"…"\}}.
     *       Same behaviour as before.</li>
     *   <li>{@code errorMessage != null} → reject outcome (BUG-CANCEL-PENDING
     *       closure Local side) →
     *       {@link AdminRequestRepository#markFailed} with
     *       {@code result_data = \{"reason":"…","applied":false,"tournamentId":"…"\}}
     *       so the platform admin sees the actual rejection reason on the
     *       AdminRequestsView card (parsable via the existing
     *       {@code result_data.reason} convention, see AdminRequestsView.readableResult)
     *       instead of waiting 30 min for the timeout.</li>
     * </ul>
     *
     * <p>The conditional {@code WHERE status = 'PENDING'} clause keeps the
     * transition idempotent on re-delivery of the same return-event.</p>
     */
    private void markClosedIfRequested(String eventId, String originatingRequestId, boolean tombstone,
                                       String tournamentId, String errorMessage) {
        if (originatingRequestId == null || originatingRequestId.isBlank()) {
            return;
        }
        if (errorMessage != null && !errorMessage.isBlank()) {
            String resultData = "{\"reason\":" + jsonString(errorMessage)
                    + ",\"applied\":false,\"tournamentId\":" + jsonString(tournamentId) + "}";
            int mutated = adminRequestRepository.markFailed(
                    originatingRequestId, resultData, java.time.Instant.now());
            if (mutated > 0) {
                log.info("Admin request {} marked FAILED by tournament-summary event {} (reason='{}')",
                        originatingRequestId, eventId, errorMessage);
            } else if (log.isDebugEnabled()) {
                log.debug("Admin request {} already resolved or unknown — markFailed returned 0 (event {}, reason='{}')",
                        originatingRequestId, eventId, errorMessage);
            }
            return;
        }
        String resultData = (tombstone ? "{\"deleted\":true" : "{\"deleted\":false,\"applied\":true")
                + ",\"tournamentId\":\"" + tournamentId + "\"}";
        int mutated = adminRequestRepository.markCompleted(
                originatingRequestId, resultData, java.time.Instant.now());
        if (mutated > 0) {
            log.info("Admin request {} marked COMPLETED by tournament-summary event {}",
                    originatingRequestId, eventId);
        } else if (log.isDebugEnabled()) {
            log.debug("Admin request {} already resolved or unknown — markCompleted returned 0 (event {})",
                    originatingRequestId, eventId);
        }
    }

    /** Minimal JSON string literal escape (mirrors the existing toJson conventions). */
    private static String jsonString(String raw) {
        if (raw == null) {
            return "null";
        }
        return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
