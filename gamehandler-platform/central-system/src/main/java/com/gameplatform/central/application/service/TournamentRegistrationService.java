package com.gameplatform.central.application.service;

import com.gameplatform.central.domain.exception.DuplicateTournamentParticipantException;
import com.gameplatform.central.domain.exception.InvalidTournamentException;
import com.gameplatform.central.domain.exception.TournamentNotFoundException;
import com.gameplatform.central.domain.exception.TournamentRegistrationClosedException;
import com.gameplatform.central.domain.exception.UserNotFoundException;
import com.gameplatform.central.domain.model.Team;
import com.gameplatform.central.domain.model.Tournament;
import com.gameplatform.central.domain.model.TournamentParticipant;
import com.gameplatform.central.domain.model.User;
import com.gameplatform.central.domain.ports.in.ListTournamentParticipantsUseCase;
import com.gameplatform.central.domain.ports.in.RegisterTournamentParticipantUseCase;
import com.gameplatform.central.domain.ports.in.UnregisterTournamentParticipantUseCase;
import com.gameplatform.central.domain.ports.out.TournamentParticipantRepository;
import com.gameplatform.central.domain.ports.out.TournamentRepository;
import com.gameplatform.central.domain.ports.out.TournamentTeamRepository;
import com.gameplatform.central.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.TeamId;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.TournamentParticipantDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service implementing the FASE 4 registration use cases (PIANO_UTENTI_TORNEI.md
 * §3.6 {@code TournamentRegistrationService}). Handles both individual and team
 * registration per locked decisions C.4 (captain via principal, included in
 * teamMembers of size {@code teamSize}) and C.7 (identity & display_name resolution).
 *
 * <p>Per the locked FASE 4 decision (C.13): <strong>no outbox events are emitted
 * in FASE 4</strong>. Member existence is NOT validated at registration time
 * (deferred to FASE 6 session start — C.7).
 */
@Service
@Transactional
public class TournamentRegistrationService implements RegisterTournamentParticipantUseCase,
        UnregisterTournamentParticipantUseCase, ListTournamentParticipantsUseCase {

    private final TournamentRepository tournamentRepository;
    private final TournamentTeamRepository tournamentTeamRepository;
    private final TournamentParticipantRepository tournamentParticipantRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public TournamentRegistrationService(TournamentRepository tournamentRepository,
                                         TournamentTeamRepository tournamentTeamRepository,
                                         TournamentParticipantRepository tournamentParticipantRepository,
                                         UserRepository userRepository,
                                         Clock clock) {
        this.tournamentRepository = tournamentRepository;
        this.tournamentTeamRepository = tournamentTeamRepository;
        this.tournamentParticipantRepository = tournamentParticipantRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Override
    public TournamentParticipantDto register(TournamentId tournamentId, UserId captainId, String teamName, List<String> teamMemberIds) {
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
        if (!teamRequest) {
            return registerIndividual(t, tournamentId, captainId);
        }
        return registerTeam(t, tournamentId, captainId, teamName, teamMemberIds);
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
            return;
        }
        Optional<Team> team = tournamentTeamRepository.findByTournamentAndMember(tournamentId, currentUserId);
        if (team.isPresent()) {
            tournamentParticipantRepository.deleteByTournamentAndParticipantId(tournamentId, team.get().getTeamId().value());
            tournamentTeamRepository.deleteById(team.get().getTeamId());
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
}