package com.gameplatform.central.application.service;

import com.gameplatform.central.domain.exception.InvalidTournamentException;
import com.gameplatform.central.domain.exception.TournamentNotFoundException;
import com.gameplatform.central.domain.model.Tournament;
import com.gameplatform.central.domain.ports.in.CancelTournamentUseCase;
import com.gameplatform.central.domain.ports.in.CreateTournamentUseCase;
import com.gameplatform.central.domain.ports.in.GetTournamentUseCase;
import com.gameplatform.central.domain.ports.in.ListTournamentsUseCase;
import com.gameplatform.central.domain.ports.in.OpenTournamentRegistrationUseCase;
import com.gameplatform.central.domain.ports.out.GameDefinitionRepository;
import com.gameplatform.central.domain.ports.out.TournamentBuildingRepository;
import com.gameplatform.central.domain.ports.out.TournamentParticipantRepository;
import com.gameplatform.central.domain.ports.out.TournamentRepository;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentFormat;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.dto.TournamentDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Application service implementing the FASE 4 tournament CRUD + lifecycle use
 * cases (PIANO_UTENTI_TORNEI.md §3.6). Central source-of-truth for {@link Tournament}.
 *
 * <p>Per the locked FASE 4 decision (C.13): <strong>no outbox events are emitted
 * in FASE 4</strong> — the 5 event record classes in shared-domain are
 * forward-declared for FASE 5/6 consumers. The {@code writeOutboxEvent} pattern
 * used by FASE 1/2 services is NOT applied here.
 */
@Service
@Transactional
public class TournamentService implements CreateTournamentUseCase, OpenTournamentRegistrationUseCase,
        CancelTournamentUseCase, GetTournamentUseCase, ListTournamentsUseCase {

    private final TournamentRepository tournamentRepository;
    private final TournamentBuildingRepository tournamentBuildingRepository;
    private final TournamentParticipantRepository tournamentParticipantRepository;
    private final GameDefinitionRepository gameDefinitionRepository;
    private final Clock clock;

    public TournamentService(TournamentRepository tournamentRepository,
                             TournamentBuildingRepository tournamentBuildingRepository,
                             TournamentParticipantRepository tournamentParticipantRepository,
                             GameDefinitionRepository gameDefinitionRepository,
                             Clock clock) {
        this.tournamentRepository = tournamentRepository;
        this.tournamentBuildingRepository = tournamentBuildingRepository;
        this.tournamentParticipantRepository = tournamentParticipantRepository;
        this.gameDefinitionRepository = gameDefinitionRepository;
        this.clock = clock;
    }

    @Override
    public TournamentDto create(Tournament tournament, List<String> buildingIds) {
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
        return toDto(saved, buildingIds, 0L);
    }

    @Override
    public TournamentDto open(TournamentId tournamentId) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new TournamentNotFoundException("Tournament not found: " + tournamentId.value()));
        Tournament opened = t.openRegistration();
        tournamentRepository.save(opened);
        return toDto(opened,
                tournamentBuildingRepository.findByTournament(tournamentId),
                tournamentParticipantRepository.countByTournament(tournamentId));
    }

    @Override
    public TournamentDto cancel(TournamentId tournamentId) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new TournamentNotFoundException("Tournament not found: " + tournamentId.value()));
        Tournament cancelled = t.cancel();
        tournamentRepository.save(cancelled);
        return toDto(cancelled,
                tournamentBuildingRepository.findByTournament(tournamentId),
                tournamentParticipantRepository.countByTournament(tournamentId));
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
}
