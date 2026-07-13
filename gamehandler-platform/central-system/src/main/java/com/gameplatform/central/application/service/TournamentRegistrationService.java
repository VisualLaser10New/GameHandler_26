package com.gameplatform.central.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.exception.DuplicateTournamentParticipantException;
import com.gameplatform.central.domain.exception.InvalidTournamentException;
import com.gameplatform.central.domain.exception.TournamentNotFoundException;
import com.gameplatform.central.domain.exception.TournamentRegistrationClosedException;
import com.gameplatform.central.domain.exception.UserNotFoundException;
import com.gameplatform.central.domain.model.OutboxEvent;
import com.gameplatform.central.domain.model.OutboxEventStatus;
import com.gameplatform.central.domain.model.Team;
import com.gameplatform.central.domain.model.Tournament;
import com.gameplatform.central.domain.model.TournamentParticipant;
import com.gameplatform.central.domain.model.User;
import com.gameplatform.central.domain.ports.in.ListTournamentParticipantsUseCase;
import com.gameplatform.central.domain.ports.in.RegisterTournamentParticipantUseCase;
import com.gameplatform.central.domain.ports.in.UnregisterTournamentParticipantUseCase;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.central.domain.ports.out.TournamentParticipantRepository;
import com.gameplatform.central.domain.ports.out.TournamentRepository;
import com.gameplatform.central.domain.ports.out.TournamentTeamRepository;
import com.gameplatform.central.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.TeamId;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.TeamMemberEntryDto;
import com.gameplatform.shared.dto.TeamMembersEventDto;
import com.gameplatform.shared.dto.TournamentParticipantDto;
import com.gameplatform.shared.dto.TournamentParticipantViewDto;
import com.gameplatform.shared.dto.TournamentParticipantsEventDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application service implementing the FASE 4 registration use cases (PIANO_UTENTI_TORNEI.md
 * §3.6 {@code TournamentRegistrationService}). Handles both individual and team
 * registration per locked decisions C.4 (captain via principal, included in
 * teamMembers of size {@code teamSize}) and C.7 (identity & display_name resolution).
 *
 * <p>FASE 7-A3: {@code register}/{@code unregister} now emit a
 * {@code TOURNAMENT_PARTICIPANTS_UPSERTED} outbox event carrying the full
 * participant snapshot for the tournament, so every active Local Server can
 * mirror the {@code tournament_participants_local} projection (delete+insert by
 * {@code tournamentId}). The {@code originatingRequestId} is {@code null} on the
 * direct REST path and non-null on the SyncEventProcessor
 * {@code PARTICIPANT_REGISTER_REQUESTED} branch §7.A.7. The repository save and
 * the outbox save commit atomically inside the class-level transaction.</p>
 */
@Service
@Transactional
public class TournamentRegistrationService implements RegisterTournamentParticipantUseCase,
        UnregisterTournamentParticipantUseCase, ListTournamentParticipantsUseCase {

    private static final String PARTICIPANTS_EVENT_TYPE = "TOURNAMENT_PARTICIPANTS_UPSERTED";
    private static final String TEAM_MEMBERS_EVENT_TYPE = "TEAM_MEMBERS_UPSERTED";

    private final TournamentRepository tournamentRepository;
    private final TournamentTeamRepository tournamentTeamRepository;
    private final TournamentParticipantRepository tournamentParticipantRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public TournamentRegistrationService(TournamentRepository tournamentRepository,
                                         TournamentTeamRepository tournamentTeamRepository,
                                         TournamentParticipantRepository tournamentParticipantRepository,
                                         UserRepository userRepository,
                                         Clock clock,
                                         OutboxEventRepository outboxEventRepository,
                                         ObjectMapper objectMapper) {
        this.tournamentRepository = tournamentRepository;
        this.tournamentTeamRepository = tournamentTeamRepository;
        this.tournamentParticipantRepository = tournamentParticipantRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Backward-compat legacy ctor (pattern {@code SyncEventProcessor:91-146}):
     * 5-arg delegating to the 7-arg production ctor with {@code null} for the
     * FASE 7-A3 outbox deps. When {@code null}, the
     * {@code TOURNAMENT_PARTICIPANTS_UPSERTED} emit is skipped (no-op),
     * preserving the historical FASE 4 behaviour for existing unit tests.
     */
    public TournamentRegistrationService(TournamentRepository tournamentRepository,
                                         TournamentTeamRepository tournamentTeamRepository,
                                         TournamentParticipantRepository tournamentParticipantRepository,
                                         UserRepository userRepository,
                                         Clock clock) {
        this(tournamentRepository, tournamentTeamRepository, tournamentParticipantRepository,
                userRepository, clock, null, null);
    }

    @Override
    public TournamentParticipantDto register(TournamentId tournamentId, UserId captainId, String teamName,
                                              List<String> teamMemberIds, String originatingRequestId) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new TournamentNotFoundException("Tournament not found: " + tournamentId.value()));
        if (t.getStatus() != TournamentStatus.OPEN_REGISTRATION) {
            throw new TournamentRegistrationClosedException("Registration is not open for tournament " + tournamentId.value());
        }
        if (captainId == null) {
            throw new InvalidTournamentException("Authenticated user could not be resolved");
        }
        boolean teamRequest = teamName != null && !teamName.isBlank()
                || (teamMemberIds != null && !teamMemberIds.isEmpty());
        TournamentParticipantDto result;
        if (!teamRequest) {
            result = registerIndividual(t, tournamentId, captainId);
        } else {
            result = registerTeam(t, tournamentId, captainId, teamName, teamMemberIds);
        }
        writeParticipantsOutbox(tournamentId, originatingRequestId);
        return result;
    }

    private TournamentParticipantDto registerIndividual(Tournament t, TournamentId tournamentId, UserId captainId) {
        if (t.isTeamBased()) {
            throw new InvalidTournamentException("Tournament requires team registration");
        }
        User u = userRepository.findById(captainId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + captainId.value()));
        String participantId = captainId.value();
        if (tournamentParticipantRepository.existsByTournamentAndParticipantId(tournamentId, participantId)) {
            throw new DuplicateTournamentParticipantException("User already registered in tournament " + tournamentId.value());
        }
        TournamentParticipant p = new TournamentParticipant(
                tournamentId, participantId, false, u.getUsername(), Instant.now(clock));
        tournamentParticipantRepository.save(p);
        return new TournamentParticipantDto(participantId, false, u.getUsername());
    }

    private TournamentParticipantDto registerTeam(Tournament t, TournamentId tournamentId, UserId captainId,
                                                   String teamName, List<String> teamMemberIds) {
        if (!t.isTeamBased()) {
            throw new InvalidTournamentException("Tournament does not allow team registration");
        }
        if (teamName == null || teamName.isBlank()) {
            throw new InvalidTournamentException("teamName must not be blank");
        }
        if (teamMemberIds == null || teamMemberIds.size() != t.getTeamSize()) {
            throw new InvalidTournamentException("teamMembers size must equal teamSize " + t.getTeamSize());
        }
        if (!teamMemberIds.contains(captainId.value())) {
            throw new InvalidTournamentException("Captain must be a team member");
        }
        if (tournamentTeamRepository.existsByTournamentAndName(tournamentId, teamName)) {
            throw new InvalidTournamentException("Team name already taken in this tournament");
        }
        TeamId teamId = new TeamId(UUID.randomUUID().toString());
        List<UserId> members = teamMemberIds.stream().map(UserId::new).toList();
        Team team = new Team(teamId, tournamentId, teamName, members, Instant.now(clock));
        tournamentTeamRepository.save(team);
        String participantId = teamId.value();
        TournamentParticipant p = new TournamentParticipant(
                tournamentId, participantId, true, teamName, Instant.now(clock));
        tournamentParticipantRepository.save(p);
        writeTeamMembersOutbox(tournamentId);
        return new TournamentParticipantDto(participantId, true, teamName);
    }

    @Override
    public void unregister(TournamentId tournamentId, UserId currentUserId) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new TournamentNotFoundException("Tournament not found: " + tournamentId.value()));
        if (t.getStatus() != TournamentStatus.OPEN_REGISTRATION) {
            throw new TournamentRegistrationClosedException("Registration is not open for tournament " + tournamentId.value());
        }
        Optional<TournamentParticipant> p = tournamentParticipantRepository
                .findByTournamentAndParticipantId(tournamentId, currentUserId.value());
        if (p.isPresent() && !p.get().isTeam()) {
            tournamentParticipantRepository.deleteByTournamentAndParticipantId(tournamentId, currentUserId.value());
            writeParticipantsOutbox(tournamentId, null);
            return;
        }
        Optional<Team> team = tournamentTeamRepository.findByTournamentAndMember(tournamentId, currentUserId);
        if (team.isPresent()) {
            tournamentParticipantRepository.deleteByTournamentAndParticipantId(tournamentId, team.get().getTeamId().value());
            tournamentTeamRepository.deleteById(team.get().getTeamId());
            writeParticipantsOutbox(tournamentId, null);
            writeTeamMembersOutbox(tournamentId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TournamentParticipantDto> listParticipants(TournamentId tournamentId) {
        if (tournamentId == null) {
            return List.of();
        }
        return tournamentParticipantRepository.findByTournament(tournamentId).stream()
                .map(p -> new TournamentParticipantDto(p.getParticipantId(), p.isTeam(), p.getDisplayName()))
                .toList();
    }

    /**
     * Serialises a {@link TournamentParticipantsEventDto} carrying the full
     * participant snapshot for the tournament and writes it to the outbox.
     * Mirrors {@code TournamentService.writeOutboxEvent}: a single UUID is
     * shared by the outbox event id and the DTO {@code eventId}. No-op when the
     * outbox deps are {@code null} (legacy test ctor).
     */
    private void writeParticipantsOutbox(TournamentId tournamentId, String originatingRequestId) {
        if (outboxEventRepository == null || objectMapper == null) {
            return;
        }
        String eventId = UUID.randomUUID().toString();
        List<TournamentParticipantViewDto> snapshot =
                Optional.ofNullable(tournamentParticipantRepository.findByTournament(tournamentId))
                        .orElse(List.of()).stream()
                        .map(p -> new TournamentParticipantViewDto(
                                p.getParticipantId(), p.isTeam(), p.getDisplayName(), p.getRegisteredAt()))
                        .collect(Collectors.toList());
        TournamentParticipantsEventDto dto = new TournamentParticipantsEventDto(
                eventId,
                PARTICIPANTS_EVENT_TYPE,
                tournamentId.value(),
                snapshot,
                originatingRequestId,
                Instant.now(clock));
        String payload;
        try {
            payload = objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize TournamentParticipantsEventDto", e);
        }
        OutboxEvent event = new OutboxEvent(
                eventId, PARTICIPANTS_EVENT_TYPE, payload, OutboxEventStatus.PENDING, Instant.now(clock), null);
        outboxEventRepository.save(event);
    }

    /**
     * Serialises a {@link TeamMembersEventDto} carrying the full per-tournament
     * team→user membership snapshot (read via
     * {@link TournamentTeamRepository#findByTournament}) and writes it to the
     * outbox. Mirrors {@link #writeParticipantsOutbox(TournamentId, String)}:
     * a single UUID is shared by the outbox event id and the DTO
     * {@code eventId}. The {@code originatingRequestId} is always {@code null}
     * on this path (BUG-TEAM-3): the admin-request closure for the registration
     * use case is driven by the parallel {@code TOURNAMENT_PARTICIPANTS_UPSERTED}
     * return event, so this event never drives an {@code admin_requests_local}
     * state transition on the Local side. No-op when the outbox deps are
     * {@code null} (legacy test ctor).
     */
    private void writeTeamMembersOutbox(TournamentId tournamentId) {
        if (outboxEventRepository == null || objectMapper == null) {
            return;
        }
        String eventId = UUID.randomUUID().toString();
        List<TeamMemberEntryDto> snapshot =
                Optional.ofNullable(tournamentTeamRepository.findByTournament(tournamentId))
                        .orElse(List.of()).stream()
                        .map(team -> new TeamMemberEntryDto(
                                team.getTeamId().value(),
                                team.getMembers().stream().map(UserId::value).toList()))
                        .collect(Collectors.toList());
        TeamMembersEventDto dto = new TeamMembersEventDto(
                eventId,
                TEAM_MEMBERS_EVENT_TYPE,
                tournamentId.value(),
                snapshot,
                null,
                Instant.now(clock));
        String payload;
        try {
            payload = objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize TeamMembersEventDto", e);
        }
        OutboxEvent event = new OutboxEvent(
                eventId, TEAM_MEMBERS_EVENT_TYPE, payload, OutboxEventStatus.PENDING, Instant.now(clock), null);
        outboxEventRepository.save(event);
    }
}