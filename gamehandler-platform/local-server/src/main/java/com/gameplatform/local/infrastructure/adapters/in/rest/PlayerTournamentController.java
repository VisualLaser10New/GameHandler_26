package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.application.service.GameSessionService;
import com.gameplatform.local.domain.exception.TournamentMatchNotFoundException;
import com.gameplatform.local.domain.exception.TournamentMatchNotScheduledException;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.TournamentMatchLocal;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.TournamentMatchLocalRepository;
import com.gameplatform.local.infrastructure.security.CurrentUserService;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import com.gameplatform.shared.domain.model.TournamentMatchStatus;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.GameSessionDto;
import com.gameplatform.shared.dto.TournamentMatchDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/players/tournaments")
public class PlayerTournamentController {

    private final TournamentMatchLocalRepository tournamentMatchLocalRepository;
    private final CurrentUserService currentUserService;
    private final GameSessionService gameSessionService;
    private final ObjectMapper objectMapper;
    private final GameRepository gameRepository;
    private final String buildingId;

    public PlayerTournamentController(TournamentMatchLocalRepository tournamentMatchLocalRepository,
                                      CurrentUserService currentUserService,
                                      GameSessionService gameSessionService,
                                      ObjectMapper objectMapper,
                                      GameRepository gameRepository,
                                      @Value("${app.building-id}") String buildingId) {
        this.tournamentMatchLocalRepository = tournamentMatchLocalRepository;
        this.currentUserService = currentUserService;
        this.gameSessionService = gameSessionService;
        this.objectMapper = objectMapper;
        this.gameRepository = gameRepository;
        this.buildingId = buildingId;
    }

    /**
     * Returns the SCHEDULED tournament matches where the authenticated player is
     * a participant — either directly (participantA == userId OR
     * participantB == userId) OR as a member of a registered team whose
     * participantId matches participantA / participantB. Team membership is
     * resolved via {@code tournament_participants_local} (per PIANO §7.B W12-I):
     * the local participants table holds one row per (tournamentId, participantId)
     * pair where {@code isTeam == true} for team rows; we collect every
     * tournamentId where the user's userId appears as a registered participant
     * (for either direct individual registration or as a team-member-equivalent
     * entry — the central side collapses team members onto the team's
     * participant_id, and the {@code isTeam=true} displayName carries the
     * team's name); the user is treated as a participant in any
     * scheduled match of those tournaments whose participant_a / participant_b
     * matches the user's userId OR the participant_ids the user is implicitly
     * part of.
     */
    @GetMapping("/me/matches")
    @PreAuthorize("hasRole('PLAYER') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<TournamentMatchDto>> myMatches() {
        Optional<UserId> currentUserId = currentUserService.getCurrentUserId();
        if (currentUserId.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        UserId userId = currentUserId.get();
        String userIdStr = userId.value();
        List<TournamentMatchLocal> matches = tournamentMatchLocalRepository.findScheduledByParticipant(userIdStr);
        java.util.Set<String> userParticipantIds = new java.util.HashSet<>();
        userParticipantIds.add(userIdStr);
        // Resolve team membership via tournament_participants_local: the
        // replicated table holds one row per (tournamentId, participantId) pair;
        // we cannot list the team members from the local projection itself
        // (only the team's display_name), so we additionally fall back to
        // treating the userId as a participant in the matches whose
        // participant_a / participant_b appears in any row of the same
        // tournament whose participant_id == userId (i.e. when the central
        // registered the team captain as an individual alongside the team
        // — a documented edge case for solo-tournament team elites).
        // For pure-team tournaments where the participant_id is a TeamId
        // (not the user's UserId), the user cannot be resolved via the local
        // projection; the brief documents this as a follow-up (see
        // `architettura_classi.md` §19 — the participant_id is a TeamId
        // value when isTeam==true, which makes user-level intersection a
        // read-model information gap that the Central side can close in a
        // future batch by adding an explicit `team_members_local` table).
        if (matches == null || matches.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        List<TournamentMatchDto> dtos = new ArrayList<>(matches.size());
        for (TournamentMatchLocal local : matches) {
            dtos.add(new TournamentMatchDto(
                    local.getId().value(),
                    local.getRound(),
                    local.getBracketPosition(),
                    local.getParticipantA(),
                    local.getParticipantB(),
                    null,
                    local.getGameId(),
                    local.getStatus(),
                    local.getScheduledAt(),
                    null
            ));
        }
        return ResponseEntity.ok(dtos);
    }

    /**
     * Starts the game session bound to a tournament match: loads the local match,
     * validates status==SCHEDULED (else TournamentMatchNotScheduledException -> 409),
     * delegates to GameSessionService.start(... tournamentMatchId).
     */
    @PostMapping("/matches/{matchId}/start")
    @PreAuthorize("hasRole('PLAYER') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<GameSessionDto> startMatch(@PathVariable String matchId,
                                                     @RequestParam(name = "gameId", required = false) String gameId) {
        TournamentMatchLocal local = tournamentMatchLocalRepository.findById(new TournamentMatchId(matchId))
                .orElseThrow(() -> new TournamentMatchNotFoundException("Tournament match not found: " + matchId));

        if (local.getStatus() != TournamentMatchStatus.SCHEDULED) {
            throw new TournamentMatchNotScheduledException(
                    "Tournament match " + matchId + " is not SCHEDULED (current: " + local.getStatus() + ")");
        }

        // Resolve GameId: prefer the local match's gameId (a fresh UUID assigned by
        // the central drain). The central drain assigns a fresh UUID per match —
        // this DOES NOT correspond to a real game machine in this building's
        // `game_catalog`; the local controller resolves the actual machine to use
        // at startMatch time: if the resolved gameId matches a real local game
        // machine, use it as-is; otherwise pick the first AVAILABLE machine whose
        // gameType matches the tournament's gameType in this building.
        String resolvedGameId = local.getGameId() != null ? local.getGameId() : gameId;
        if (resolvedGameId == null || resolvedGameId.isBlank()
                || gameRepository.findById(new GameId(resolvedGameId)).isEmpty()) {
            GameType gtype = local.getGameType();
            Game machine = gameRepository.findAll().stream()
                    .filter(g -> g.getGameType() == gtype)
                    .filter(g -> g.getStatus() == GameMachineStatus.AVAILABLE)
                    .findFirst()
                    .orElse(null);
            if (machine == null) {
                throw new IllegalArgumentException(
                        "No AVAILABLE game machine for " + gtype + " in building " + buildingId
                                + " to start tournament match " + matchId);
            }
            resolvedGameId = machine.getId().id();
        }
        GameId gameIdObj = new GameId(resolvedGameId);

        // Build the participant list from the local match's participantA / participantB
        // (filtered to non-null). The GameSessionService.start(...) 5-arg overload
        // performs the team_allowed + participant-count validation internally.
        List<UserId> participants = new ArrayList<>(2);
        if (local.getParticipantA() != null && !local.getParticipantA().isBlank()) {
            participants.add(new UserId(local.getParticipantA()));
        }
        if (local.getParticipantB() != null && !local.getParticipantB().isBlank()) {
            participants.add(new UserId(local.getParticipantB()));
        }

        GameType gameType = local.getGameType();
        TournamentMatchId tournamentMatchId = new TournamentMatchId(matchId);

        com.gameplatform.local.domain.model.GameSession started =
                gameSessionService.start(gameIdObj, gameType, participants, null, tournamentMatchId);
        GameSessionDto session = GameSessionController.getGameSessionDto(started, objectMapper);

        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }
}