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
        UpdateTournamentUseCase, DeleteTournamentUseCase {

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
        writeOutboxEvent(saved, buildingIds, 0L, false, originatingRequestId);
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
        writeOutboxEvent(opened, openedBuildings, openedParticipants, false, originatingRequestId);
        return toDto(opened, openedBuildings, openedParticipants);
    }

    @Override
    public TournamentDto cancel(TournamentId tournamentId, String originatingRequestId) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new TournamentNotFoundException("Tournament not found: " + tournamentId.value()));
        Tournament cancelled = t.cancel();
        tournamentRepository.save(cancelled);
        List<String> cancelledBuildings = tournamentBuildingRepository.findByTournament(tournamentId);
        long cancelledParticipants = tournamentParticipantRepository.countByTournament(tournamentId);
        writeOutboxEvent(cancelled, cancelledBuildings, cancelledParticipants, false, originatingRequestId);
        return toDto(cancelled, cancelledBuildings, cancelledParticipants);
    }

    @Override
    @Transactional
    public TournamentDto update(TournamentId tournamentId, String name, Instant startsAt,
                                List<String> buildingIds, String originatingRequestId) {
        Tournament existing = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new TournamentNotFoundException("Tournament not found: " + tournamentId.value()));
        Tournament updated = existing.update(name, startsAt);
        tournamentRepository.save(updated);
        tournamentBuildingRepository.deleteByTournament(tournamentId);
        tournamentBuildingRepository.saveAll(tournamentId, buildingIds);
        writeOutboxEvent(updated, buildingIds, 0L, false, originatingRequestId);
        return toDto(updated, buildingIds, 0);
    }

    @Override
    @Transactional
    public void delete(TournamentId tournamentId, String originatingRequestId) {
        Tournament existing = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new TournamentNotFoundException("Tournament not found: " + tournamentId.value()));
        if (existing.getStatus() != TournamentStatus.DRAFT) {
            throw new InvalidTournamentStateException("Cannot delete tournament not in DRAFT: " + existing.getStatus());
        }
        List<String> buildings = tournamentBuildingRepository.findByTournament(tournamentId);
        tournamentBuildingRepository.deleteByTournament(tournamentId);
        tournamentRepository.deleteById(tournamentId);
        writeOutboxEvent(existing, buildings, 0L, true, originatingRequestId);
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
                                  boolean deleted, String originatingRequestId) {
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
                originatingRequestId);
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
}
