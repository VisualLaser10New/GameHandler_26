package com.gameplatform.central.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.exception.InvalidTournamentException;
import com.gameplatform.central.domain.exception.InvalidTournamentStateException;
import com.gameplatform.central.domain.exception.TournamentNotFoundException;
import com.gameplatform.central.domain.model.OutboxEvent;
import com.gameplatform.central.domain.model.OutboxEventStatus;
import com.gameplatform.central.domain.model.Tournament;
import com.gameplatform.central.domain.ports.in.CancelTournamentUseCase;
import com.gameplatform.central.domain.ports.in.CreateTournamentUseCase;
import com.gameplatform.central.domain.ports.in.DeleteTournamentUseCase;
import com.gameplatform.central.domain.ports.in.EmitTournamentSummaryUseCase;
import com.gameplatform.central.domain.ports.in.GetTournamentUseCase;
import com.gameplatform.central.domain.ports.in.ListTournamentsUseCase;
import com.gameplatform.central.domain.ports.in.OpenTournamentRegistrationUseCase;
import com.gameplatform.central.domain.ports.in.UpdateTournamentUseCase;
import com.gameplatform.central.domain.ports.out.GameDefinitionRepository;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.central.domain.ports.out.TournamentBuildingRepository;
import com.gameplatform.central.domain.ports.out.TournamentParticipantRepository;
import com.gameplatform.central.domain.ports.out.TournamentRepository;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentFormat;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.dto.TournamentDto;
import com.gameplatform.shared.dto.TournamentSummaryEventDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service implementing the FASE 4 tournament CRUD + lifecycle use
 * cases (PIANO_UTENTI_TORNEI.md §3.6). Central source-of-truth for {@link Tournament}.
 *
 * <p>The {@code update} and {@code delete} use cases emit a
 * {@code TOURNAMENT_SUMMARY_UPSERTED} outbox event (with {@code deleted=true}
 * acting as a tombstone for deletes) so every active Local Server can mirror
 * the tournament summary projection. The repository save and the outbox save
 * commit atomically inside the class-level transaction.
 */
@Service
@Transactional
public class TournamentService implements CreateTournamentUseCase, OpenTournamentRegistrationUseCase,
        CancelTournamentUseCase, GetTournamentUseCase, ListTournamentsUseCase,
        UpdateTournamentUseCase, DeleteTournamentUseCase, EmitTournamentSummaryUseCase {

    private static final Logger log = LoggerFactory.getLogger(TournamentService.class);

    private static final String SUMMARY_EVENT_TYPE = "TOURNAMENT_SUMMARY_UPSERTED";

    private final TournamentRepository tournamentRepository;
    private final TournamentBuildingRepository tournamentBuildingRepository;
    private final TournamentParticipantRepository tournamentParticipantRepository;
    private final GameDefinitionRepository gameDefinitionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public TournamentService(TournamentRepository tournamentRepository,
                             TournamentBuildingRepository tournamentBuildingRepository,
                             TournamentParticipantRepository tournamentParticipantRepository,
                             GameDefinitionRepository gameDefinitionRepository,
                             Clock clock,
                             OutboxEventRepository outboxEventRepository,
                             ObjectMapper objectMapper) {
        this.tournamentRepository = tournamentRepository;
        this.tournamentBuildingRepository = tournamentBuildingRepository;
        this.tournamentParticipantRepository = tournamentParticipantRepository;
        this.gameDefinitionRepository = gameDefinitionRepository;
        this.clock = clock;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    TournamentService(TournamentRepository tournamentRepository,
                      TournamentBuildingRepository tournamentBuildingRepository,
                      TournamentParticipantRepository tournamentParticipantRepository,
                      GameDefinitionRepository gameDefinitionRepository,
                      Clock clock) {
        this(tournamentRepository, tournamentBuildingRepository, tournamentParticipantRepository,
                gameDefinitionRepository, clock, null, null);
    }

    @Override
    public TournamentDto create(Tournament tournament, List<String> buildingIds, String originatingRequestId) {
        if (tournament == null) {
            throw new InvalidTournamentException("Tournament cannot be null");
        }
        if (buildingIds == null || buildingIds.size() < 2) {
            throw new InvalidTournamentException("At least 2 buildings are required");
        }
        if (buildingIds.stream().anyMatch(b -> b == null || b.isBlank())) {
            throw new InvalidTournamentException("buildingIds must not contain blank entries");
        }
        if (tournament.getStartsAt() == null) {
            throw new InvalidTournamentException("startsAt cannot be null");
        }
        GameType gameType = tournament.getGameType();
        com.gameplatform.central.domain.model.GameDefinition gd = gameDefinitionRepository
                .findByGameType(gameType)
                .orElseThrow(() -> new InvalidTournamentException("Game type not defined: " + gameType));
        if (tournament.isTeamBased() && !gd.isTeamAllowed()) {
            throw new InvalidTournamentException("Game " + gameType + " does not allow team-based tournaments");
        }
        if (!tournament.isTeamBased() && tournament.getTeamSize() != 1) {
            throw new InvalidTournamentException("Individual tournament must have teamSize == 1");
        }
        if (tournament.isTeamBased() && tournament.getTeamSize() < 2) {
            throw new InvalidTournamentException("Team-based tournament must have teamSize >= 2");
        }
        Tournament draft = new Tournament(
                tournament.getTournamentId(),
                tournament.getName(),
                tournament.getGameType(),
                tournament.isTeamBased(),
                tournament.getTeamSize(),
                TournamentFormat.SINGLE_ELIMINATION,
                TournamentStatus.DRAFT,
                tournament.getStartsAt(),
                null,
                tournament.getCreatedBy(),
                Instant.now(clock));
        Tournament saved = tournamentRepository.save(draft);
        tournamentBuildingRepository.saveAll(saved.getTournamentId(), buildingIds);
        writeOutboxEvent(saved, buildingIds, 0L, false, originatingRequestId, null);
        return toDto(saved, buildingIds, 0L);
    }

    @Override
    public TournamentDto open(TournamentId tournamentId, String originatingRequestId) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new TournamentNotFoundException("Tournament not found: " + tournamentId.value()));
        Tournament opened = t.openRegistration();
        tournamentRepository.save(opened);
        List<String> openedBuildings = tournamentBuildingRepository.findByTournament(tournamentId);
        long openedParticipants = tournamentParticipantRepository.countByTournament(tournamentId);
        writeOutboxEvent(opened, openedBuildings, openedParticipants, false, originatingRequestId, null);
        return toDto(opened, openedBuildings, openedParticipants);
    }

    @Override
    public TournamentDto cancel(TournamentId tournamentId, String originatingRequestId) {
        // BUG-CANCEL-PENDING (root cause B): if the Central use case refuses the
        // cancel — because the tournament is in a {@code TournamentStatus} that
        // {@link Tournament#cancel()} does not admit (anything other than
        // DRAFT/OPEN_REGISTRATION, e.g. IN_PROGRESS/COMPLETED/CANCELLED), or
        // because the tournament does not exist — the {@link
        // InvalidTournamentStateException} / {@link TournamentNotFoundException}
        // would previously propagate up through {@code SyncEventProcessor#processOne}
        // to {@code SyncReceiverService#receiveSyncPayload}, where the poison-
        // isolation catch would mark the incoming event id as processed WITHOUT
        // emitting any return outbox event. The Local {@code admin_requests_local}
        // row would then stay PENDING — the only thing closing it was the
        // {@code AdminRequestTimeoutService} 30 min later, surfacing to the
        // platform admin as a vague "Operation not confirmed within timeout —
        // (reason: TIMEOUT)" card on AdminRequestsView. To close the loop
        // immediately (a few seconds via the standard summary-replication drainer)
        // and to surface the ACTUAL rejection reason ("Cannot cancel from
        // status COMPLETED" / "Tournament not found: …"), we catch the
        // rejection here, emit a {@code TOURNAMENT_SUMMARY_UPSERTED} return
        // event carrying {@code originatingRequestId} + {@code errorMessage},
        // and return {@code null}. The matching return event reaches the Local
        // {@link com.gameplatform.local.application.service.TournamentSummarySyncService},
        // which now treats a non-null {@code errorMessage} as a FAILURE marker
        // and calls {@code adminRequestRepository.markFailed(requestId, reason…)}.
        // The exception is NOT re-thrown so the surrounding @Transactional(REQUIRES_NEW)
        // tx commits the outbox row (poison isolation is preserved by the
        // LoopExclusive sync code — we just choose to bind a return event to the
        // originating request id instead of swallowing the failure).
        Tournament current = tournamentRepository.findById(tournamentId).orElse(null);
        if (current == null) {
            String reason = "Tournament not found: " + tournamentId.value();
            // Emit a tombstone (deleted=true) return event: the Local delete-by-PK
            // is a no-op when the projection row is missing, but the
            // markFailedIfRequested hook still fires and closes the admin-request.
            writeOutboxTombstoneEvent(tournamentId, originatingRequestId, reason);
            log.warn("TOURNAMENT_CANCEL_REQUESTED rejected by tournament state machine — "
                            + "tournament not found; emitted FAILED return event "
                            + "(originatingRequestId={}, tournamentId={}, reason='{}')",
                    originatingRequestId, tournamentId.value(), reason);
            return null;
        }
        try {
            Tournament cancelled = current.cancel();
            tournamentRepository.save(cancelled);
            List<String> cancelledBuildings = tournamentBuildingRepository.findByTournament(tournamentId);
            long cancelledParticipants = tournamentParticipantRepository.countByTournament(tournamentId);
            writeOutboxEvent(cancelled, cancelledBuildings, cancelledParticipants, false, originatingRequestId, null);
            return toDto(cancelled, cancelledBuildings, cancelledParticipants);
        } catch (InvalidTournamentStateException ex) {
            String reason = ex.getMessage();
            List<String> buildings = tournamentBuildingRepository.findByTournament(tournamentId);
            long participantsCount = tournamentParticipantRepository.countByTournament(tournamentId);
            // Emit the UNCHANGED tournament snapshot carrying the rejection reason
            // (errorMessage != null) so the Local markFailedIfRequested hook
            // closes the matching admin_requests_local row as FAILED.
            writeOutboxEvent(current, buildings, participantsCount, false, originatingRequestId, reason);
            log.warn("TOURNAMENT_CANCEL_REQUESTED rejected by tournament state machine — "
                            + "emitted FAILED return event "
                            + "(originatingRequestId={}, tournamentId={}, currentStatus={}, reason='{}')",
                    originatingRequestId, tournamentId.value(), current.getStatus(), reason);
            return null;
        }
    }

    @Override
    @Transactional
    public TournamentDto update(TournamentId tournamentId, String name, Instant startsAt,
                                List<String> buildingIds, String originatingRequestId) {
        // BUG-UPDATE-PENDING (mirror of BUG-CANCEL-PENDING, root cause B): if
        // the Central update use case refuses the mutation — because the
        // tournament is in a {@code TournamentStatus} that {@link
        // Tournament#update(String, Instant)} does not admit (anything other
        // than DRAFT), or because the tournament does not exist — the
        // {@link InvalidTournamentStateException} / not-found case would
        // previously propagate up through {@code SyncEventProcessor#processOne}
        // to {@code SyncReceiverService#receiveSyncPayload}, where the poison-
        // isolation catch marked the incoming event id as processed WITHOUT
        // emitting any return outbox event. The Local {@code
        // admin_requests_local} row would then stay PENDING — the only thing
        // closing it was the {@code AdminRequestTimeoutService} 30 min later,
        // surfacing as a vague "Operation not confirmed within timeout —
        // (reason: TIMEOUT)" card. We catch the rejection here, emit a
        // {@code TOURNAMENT_SUMMARY_UPSERTED} return event carrying {@code
        // originatingRequestId} + {@code errorMessage}, and return {@code null}
        // (same pattern as {@link #cancel}). The matching return event reaches
        // the Local {@link com.gameplatform.local.application.service.TournamentSummarySyncService},
        // which treats a non-null {@code errorMessage} as a FAILURE marker and
        // calls {@code adminRequestRepository.markFailed}. The exception is NOT
        // re-thrown so the surrounding {@code @Transactional(REQUIRES_NEW)} tx
        // commits the outbox row.
        Tournament existing = tournamentRepository.findById(tournamentId).orElse(null);
        if (existing == null) {
            String reason = "Tournament not found: " + tournamentId.value();
            // Emit a tombstone (deleted=true) return event: the Local
            // delete-by-PK is a no-op when the projection row is missing, but
            // the markFailedIfRequested hook still fires and closes the
            // admin-request.
            writeOutboxTombstoneEvent(tournamentId, originatingRequestId, reason);
            log.warn("TOURNAMENT_UPDATE_REQUESTED rejected by tournament state machine — "
                            + "tournament not found; emitted FAILED return event "
                            + "(originatingRequestId={}, tournamentId={}, reason='{}')",
                    originatingRequestId, tournamentId.value(), reason);
            return null;
        }
        try {
            Tournament updated = existing.update(name, startsAt);
            tournamentRepository.save(updated);
            tournamentBuildingRepository.deleteByTournament(tournamentId);
            tournamentBuildingRepository.saveAll(tournamentId, buildingIds);
            writeOutboxEvent(updated, buildingIds, 0L, false, originatingRequestId, null);
            return toDto(updated, buildingIds, 0);
        } catch (InvalidTournamentStateException ex) {
            String reason = ex.getMessage();
            List<String> buildings = tournamentBuildingRepository.findByTournament(tournamentId);
            long participantsCount = tournamentParticipantRepository.countByTournament(tournamentId);
            // Emit the UNCHANGED tournament snapshot (existing, NOT updated)
            // carrying the rejection reason (errorMessage != null) — note the
            // buildingIds argument is intentionally NOT used here: it holds the
            // PROPOSED new buildings, but the tournament was not mutated, so
            // the snapshot must reflect the current persisted buildings. The
            // Local markFailedIfRequested hook closes the matching
            // admin_requests_local row as FAILED.
            writeOutboxEvent(existing, buildings, participantsCount, false, originatingRequestId, reason);
            log.warn("TOURNAMENT_UPDATE_REQUESTED rejected by tournament state machine — "
                            + "emitted FAILED return event "
                            + "(originatingRequestId={}, tournamentId={}, currentStatus={}, reason='{}')",
                    originatingRequestId, tournamentId.value(), existing.getStatus(), reason);
            return null;
        }
    }

    @Override
    @Transactional
    public void delete(TournamentId tournamentId, String originatingRequestId) {
        // BUG-DELETE-PENDING (mirror of BUG-CANCEL-PENDING, root cause B): if
        // the Central delete use case refuses the deletion — because the
        // tournament is NOT in {@code TournamentStatus#DRAFT} (the inline
        // guard below), or because the tournament does not exist — the
        // {@link InvalidTournamentStateException} / not-found case would
        // previously propagate up through {@code SyncEventProcessor#processOne}
        // to {@code SyncReceiverService#receiveSyncPayload}, where the poison-
        // isolation catch marked the incoming event id as processed WITHOUT
        // emitting any return outbox event. The Local {@code
        // admin_requests_local} row would then stay PENDING — the only thing
        // closing it was the {@code AdminRequestTimeoutService} 30 min later,
        // surfacing as a vague "Operation not confirmed within timeout —
        // (reason: TIMEOUT)" card. We catch the rejection here, emit a
        // {@code TOURNAMENT_SUMMARY_UPSERTED} return event carrying {@code
        // originatingRequestId} + {@code errorMessage}, and return (same
        // pattern as {@link #cancel}). The matching return event reaches the
        // Local {@link com.gameplatform.local.application.service.TournamentSummarySyncService},
        // which treats a non-null {@code errorMessage} as a FAILURE marker and
        // calls {@code adminRequestRepository.markFailed}. The exception is NOT
        // re-thrown so the surrounding {@code @Transactional(REQUIRES_NEW)} tx
        // commits the outbox row. NOTE: the {@code Tournament} domain model has
        // no {@code delete()} transition method, so the DRAFT guard is enforced
        // inline here (the throw is intentionally wrapped in the try so the
        // adjacent catch contains it, mirroring {@link #cancel}'s try-catch
        // around {@link Tournament#cancel()}).
        Tournament existing = tournamentRepository.findById(tournamentId).orElse(null);
        if (existing == null) {
            String reason = "Tournament not found: " + tournamentId.value();
            // Emit a tombstone (deleted=true) return event: the Local
            // delete-by-PK is a no-op when the projection row is missing, but
            // the markFailedIfRequested hook still fires and closes the
            // admin-request.
            writeOutboxTombstoneEvent(tournamentId, originatingRequestId, reason);
            log.warn("TOURNAMENT_DELETE_REQUESTED rejected by tournament state machine — "
                            + "tournament not found; emitted FAILED return event "
                            + "(originatingRequestId={}, tournamentId={}, reason='{}')",
                    originatingRequestId, tournamentId.value(), reason);
            return;
        }
        try {
            if (existing.getStatus() != TournamentStatus.DRAFT) {
                throw new InvalidTournamentStateException("Cannot delete tournament not in DRAFT: " + existing.getStatus());
            }
            List<String> buildings = tournamentBuildingRepository.findByTournament(tournamentId);
            tournamentBuildingRepository.deleteByTournament(tournamentId);
            tournamentRepository.deleteById(tournamentId);
            writeOutboxEvent(existing, buildings, 0L, true, originatingRequestId, null);
        } catch (InvalidTournamentStateException ex) {
            String reason = ex.getMessage();
            List<String> buildings = tournamentBuildingRepository.findByTournament(tournamentId);
            long participantsCount = tournamentParticipantRepository.countByTournament(tournamentId);
            // Emit the UNCHANGED tournament snapshot (deleted=false: the
            // tournament was NOT deleted because the guard refused it, so the
            // Local projection must be UPSERTED with the unchanged snapshot,
            // NOT tombstoned) carrying the rejection reason (errorMessage != null)
            // so the Local markFailedIfRequested hook closes the matching
            // admin_requests_local row as FAILED.
            writeOutboxEvent(existing, buildings, participantsCount, false, originatingRequestId, reason);
            log.warn("TOURNAMENT_DELETE_REQUESTED rejected by tournament state machine — "
                            + "emitted FAILED return event "
                            + "(originatingRequestId={}, tournamentId={}, currentStatus={}, reason='{}')",
                    originatingRequestId, tournamentId.value(), existing.getStatus(), reason);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TournamentDto> getById(TournamentId id) {
        if (id == null) {
            return Optional.empty();
        }
        return tournamentRepository.findById(id)
                .map(t -> toDto(t,
                        tournamentBuildingRepository.findByTournament(t.getTournamentId()),
                        tournamentParticipantRepository.countByTournament(t.getTournamentId())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TournamentDto> findAll() {
        return tournamentRepository.findAll().stream()
                .map(t -> toDto(t,
                        tournamentBuildingRepository.findByTournament(t.getTournamentId()),
                        tournamentParticipantRepository.countByTournament(t.getTournamentId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TournamentDto> findByStatus(TournamentStatus status) {
        if (status == null) {
            return List.of();
        }
        return tournamentRepository.findByStatus(status).stream()
                .map(t -> toDto(t,
                        tournamentBuildingRepository.findByTournament(t.getTournamentId()),
                        tournamentParticipantRepository.countByTournament(t.getTournamentId())))
                .toList();
    }

    private TournamentDto toDto(Tournament t, List<String> buildings, long count) {
        return new TournamentDto(
                t.getTournamentId().value(),
                t.getName(),
                t.getGameType(),
                t.isTeamBased(),
                t.getTeamSize(),
                t.getStatus(),
                t.getStartsAt(),
                t.getEndsAt(),
                buildings,
                (int) count);
    }

    private void writeOutboxEvent(Tournament t, List<String> buildings, long participantsCount,
                                  boolean deleted, String originatingRequestId, String errorMessage) {
        if (outboxEventRepository == null || objectMapper == null) {
            return;
        }
        String eventId = UUID.randomUUID().toString();
        TournamentSummaryEventDto dto = new TournamentSummaryEventDto(
                eventId,
                SUMMARY_EVENT_TYPE,
                t.getTournamentId().value(),
                t.getName(),
                t.getGameType(),
                t.isTeamBased(),
                t.getTeamSize(),
                t.getStatus(),
                t.getStartsAt(),
                t.getEndsAt(),
                buildings,
                (int) participantsCount,
                Instant.now(clock),
                deleted,
                originatingRequestId,
                errorMessage);
        String payload;
        try {
            payload = objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize TournamentSummaryEventDto", e);
        }
        OutboxEvent event = new OutboxEvent(
                eventId, SUMMARY_EVENT_TYPE, payload, OutboxEventStatus.PENDING, Instant.now(clock), null);
        outboxEventRepository.save(event);
    }

    /**
     * Emits a {@code TOURNAMENT_SUMMARY_UPSERTED} tombstone return event
     * ({@code deleted=true}) carrying the rejection {@code reason} in
     * {@code errorMessage} so a {@code *_REQUESTED} admin action refused
     * because the tournament was not found still drives the Local
     * {@code admin_requests_local} row to {@code FAILED} with the readable
     * reason. The Local {@code TournamentSummarySyncService} upsert path is a
     * physical {@code deleteById (tournamentId)} no-op when the projection row
     * does not exist (idempotent), while the markFailedIfRequested hook runs
     * unconditionally for {@code originatingRequestId != null}.
     *
     * <p>Local-only fields not retrievable from a missing entity
     * ({@code name}/{@code gameType}/{@code startsAt}/…) are filled with safe
     * placeholders; they are NOT consumed by the tombstone branch on the Local
     * side, which only reads {@code tournamentId}, {@code originatingRequestId}
     * and {@code errorMessage}.</p>
     */
    private void writeOutboxTombstoneEvent(TournamentId tournamentId, String originatingRequestId, String reason) {
        if (outboxEventRepository == null || objectMapper == null) {
            return;
        }
        String eventId = UUID.randomUUID().toString();
        TournamentSummaryEventDto dto = new TournamentSummaryEventDto(
                eventId,
                SUMMARY_EVENT_TYPE,
                tournamentId.value(),
                "(not-found)",
                null,
                false,
                1,
                TournamentStatus.DRAFT,
                Instant.now(clock),
                null,
                List.<String>of(),
                0,
                Instant.now(clock),
                true,
                originatingRequestId,
                reason);
        String payload;
        try {
            payload = objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize TournamentSummaryEventDto", e);
        }
        OutboxEvent event = new OutboxEvent(
                eventId, SUMMARY_EVENT_TYPE, payload, OutboxEventStatus.PENDING, Instant.now(clock), null);
        outboxEventRepository.save(event);
    }

    /**
     * Emits a {@code TOURNAMENT_SUMMARY_UPSERTED} outbox event carrying the
     * current snapshot of the tournament (status, buildings, participantsCount)
     * with the supplied {@code originatingRequestId}. Used as the special-ack
     * return event for admin-request flows whose primary use case does not
     * itself emit a summary upsert (e.g. SCHEDULE: the schedule use case emits
     * {@code TOURNAMENT_MATCH_SCHEDULED} rows which do NOT carry the
     * originatingRequestId, so the Local {@code admin_requests_local} row
     * would stay PENDING; this method closes that gap — BUG-SCHEDULE-REQUEST-ID).
     *
     * <p>Loads the tournament (404 if absent), the building ids and the
     * participants count, then delegates to {@link #writeOutboxEvent}. The
     * outbox save is atomic with the caller's transaction ({@code @Transactional}
     * on the caller side — e.g. {@code SyncEventProcessor.handleTournamentScheduleRequested}
     * runs inside {@code processOne}'s {@code REQUIRES_NEW}). No-op (logs a
     * warning) when the outbox deps are {@code null} (legacy test ctor).</p>
     */
    @Override
    @Transactional
    public void emitSummary(TournamentId tournamentId, String originatingRequestId) {
        if (outboxEventRepository == null || objectMapper == null) {
            return;
        }
        if (tournamentId == null) {
            return;
        }
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new TournamentNotFoundException("Tournament not found: " + tournamentId.value()));
        List<String> buildings = tournamentBuildingRepository.findByTournament(tournamentId);
        long participantsCount = tournamentParticipantRepository.countByTournament(tournamentId);
        writeOutboxEvent(t, buildings, participantsCount, false, originatingRequestId, null);
    }
}
