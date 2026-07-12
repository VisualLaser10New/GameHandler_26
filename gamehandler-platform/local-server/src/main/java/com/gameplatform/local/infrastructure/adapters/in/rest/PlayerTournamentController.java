package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.application.service.GameSessionService;
import com.gameplatform.local.domain.exception.TournamentMatchNotFoundException;
import com.gameplatform.local.domain.exception.TournamentMatchNotScheduledException;
import com.gameplatform.local.domain.model.TournamentMatchLocal;
import com.gameplatform.local.domain.ports.out.TournamentMatchLocalRepository;
import com.gameplatform.local.infrastructure.security.CurrentUserService;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import com.gameplatform.shared.domain.model.TournamentMatchStatus;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.GameSessionDto;
import com.gameplatform.shared.dto.TournamentMatchDto;
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

    public PlayerTournamentController(TournamentMatchLocalRepository tournamentMatchLocalRepository,
                                      CurrentUserService currentUserService,
                                      GameSessionService gameSessionService,
                                      ObjectMapper objectMapper) {
        this.tournamentMatchLocalRepository = tournamentMatchLocalRepository;
        this.currentUserService = currentUserService;
        this.gameSessionService = gameSessionService;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns the SCHEDULED tournament matches where the authenticated player is
     * a direct participant (participant_a == userId OR participant_b == userId).
     * Ambiguity F: team matches where the user is not a direct participant
     * cannot be resolved here and are NOT returned.
     */
    @GetMapping("/me/matches")
    @PreAuthorize("hasRole('PLAYER')")
    public ResponseEntity<List<TournamentMatchDto>> myMatches() {
        Optional<UserId> currentUserId = currentUserService.getCurrentUserId();
        if (currentUserId.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        List<TournamentMatchLocal> matches = tournamentMatchLocalRepository.findScheduledByParticipant(currentUserId.get().value());
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
    @PreAuthorize("hasRole('PLAYER')")
    public ResponseEntity<GameSessionDto> startMatch(@PathVariable String matchId,
                                                     @RequestParam(name = "gameId", required = false) String gameId) {
        TournamentMatchLocal local = tournamentMatchLocalRepository.findById(new TournamentMatchId(matchId))
                .orElseThrow(() -> new TournamentMatchNotFoundException("Tournament match not found: " + matchId));

        if (local.getStatus() != TournamentMatchStatus.SCHEDULED) {
            throw new TournamentMatchNotScheduledException(
                    "Tournament match " + matchId + " is not SCHEDULED (current: " + local.getStatus() + ")");
        }

        // Resolve GameId: prefer the local match's gameId (set by the central drain
        // branch before pushing); fall back to the optional @RequestParam gameId.
        String resolvedGameId = local.getGameId() != null && !local.getGameId().isBlank()
                ? local.getGameId()
                : gameId;
        if (resolvedGameId == null || resolvedGameId.isBlank()) {
            throw new IllegalArgumentException(
                    "Cannot resolve gameId for tournament match " + matchId
                    + " (local gameId is null and no gameId request param supplied)");
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