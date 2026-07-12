package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.application.service.GameSessionService;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.ports.in.EndGameSessionUseCase;
import com.gameplatform.local.domain.ports.in.PauseGameSessionUseCase;
import com.gameplatform.local.domain.ports.in.ResumeGameSessionUseCase;
import com.gameplatform.local.domain.ports.in.StartGameSessionUseCase;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.ReservationId;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.result.GameResult;
import com.gameplatform.shared.dto.CreateSessionRequestDto;
import com.gameplatform.shared.dto.GameSessionDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.gameplatform.local.domain.ports.in.CreateLobbyUseCase;
import com.gameplatform.local.domain.ports.in.JoinLobbyUseCase;
import com.gameplatform.local.domain.ports.in.StartLobbyUseCase;
import com.gameplatform.local.domain.ports.in.CancelLobbyUseCase;
import com.gameplatform.local.domain.ports.in.GetActiveLobbyUseCase;
import com.gameplatform.shared.dto.JoinSessionRequestDto;
import java.util.List;

@RestController
@RequestMapping("/api/sessions")
@PreAuthorize("hasRole('PLAYER')")
public class GameSessionController {

    private final StartGameSessionUseCase startGameSessionUseCase;
    private final GameSessionService gameSessionService;
    private final EndGameSessionUseCase endGameSessionUseCase;
    private final PauseGameSessionUseCase pauseGameSessionUseCase;
    private final ResumeGameSessionUseCase resumeGameSessionUseCase;
    private final CreateLobbyUseCase createLobbyUseCase;
    private final JoinLobbyUseCase joinLobbyUseCase;
    private final StartLobbyUseCase startLobbyUseCase;
    private final CancelLobbyUseCase cancelLobbyUseCase;
    private final GetActiveLobbyUseCase getActiveLobbyUseCase;
    private final ObjectMapper objectMapper;

    public GameSessionController(
            StartGameSessionUseCase startGameSessionUseCase,
            GameSessionService gameSessionService,
            EndGameSessionUseCase endGameSessionUseCase,
            PauseGameSessionUseCase pauseGameSessionUseCase,
            ResumeGameSessionUseCase resumeGameSessionUseCase,
            CreateLobbyUseCase createLobbyUseCase,
            JoinLobbyUseCase joinLobbyUseCase,
            StartLobbyUseCase startLobbyUseCase,
            CancelLobbyUseCase cancelLobbyUseCase,
            GetActiveLobbyUseCase getActiveLobbyUseCase,
            ObjectMapper objectMapper) {
        this.startGameSessionUseCase = startGameSessionUseCase;
        this.gameSessionService = gameSessionService;
        this.endGameSessionUseCase = endGameSessionUseCase;
        this.pauseGameSessionUseCase = pauseGameSessionUseCase;
        this.resumeGameSessionUseCase = resumeGameSessionUseCase;
        this.createLobbyUseCase = createLobbyUseCase;
        this.joinLobbyUseCase = joinLobbyUseCase;
        this.startLobbyUseCase = startLobbyUseCase;
        this.cancelLobbyUseCase = cancelLobbyUseCase;
        this.getActiveLobbyUseCase = getActiveLobbyUseCase;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/start")
    public ResponseEntity<GameSessionDto> start(@RequestBody CreateSessionRequestDto req) {
        List<UserId> participants = req.participants() != null
                ? req.participants().stream().map(UserId::new).toList()
                : List.of();

        ReservationId reservationId = req.reservationId() != null && !req.reservationId().isBlank()
                ? new ReservationId(req.reservationId())
                : null;

        // FASE 6 — extract the optional tournamentMatchId and call the 5-arg
        // tournament-aware start overload on the concrete GameSessionService
        // (Q4 — the in-port only exposes the 4-arg signature). When the
        // tournamentMatchId is null the 5-arg overload behaves identically to
        // the 4-arg.
        TournamentMatchId tournamentMatchId = req.tournamentMatchId() != null
                && !req.tournamentMatchId().isBlank()
                ? new TournamentMatchId(req.tournamentMatchId())
                : null;

        GameSession session = gameSessionService.start(
                new GameId(req.gameId()),
                req.gameType(),
                participants,
                reservationId,
                tournamentMatchId
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(session));
    }

    @PostMapping("/lobby")
    public ResponseEntity<GameSessionDto> createLobby(@RequestBody CreateSessionRequestDto req) {
        List<UserId> participants = req.participants() != null
                ? req.participants().stream().map(UserId::new).toList()
                : List.of();
        UserId creatorId = participants.isEmpty() ? new UserId("creator") : participants.get(0);

        GameSession session = createLobbyUseCase.createLobby(
                new GameId(req.gameId()),
                req.gameType(),
                creatorId
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(session));
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<GameSessionDto> join(@PathVariable String id, @RequestBody JoinSessionRequestDto req) {
        GameSession session = joinLobbyUseCase.joinLobby(new GameSessionId(id), new UserId(req.userId()));
        return ResponseEntity.ok(toDto(session));
    }

    @PostMapping("/{id}/start-lobby")
    public ResponseEntity<GameSessionDto> startLobby(@PathVariable String id) {
        GameSession session = startLobbyUseCase.startLobby(new GameSessionId(id));
        return ResponseEntity.ok(toDto(session));
    }

    @PostMapping("/{id}/cancel-lobby")
    public ResponseEntity<GameSessionDto> cancelLobby(@PathVariable String id, @RequestBody JoinSessionRequestDto req) {
        GameSession session = cancelLobbyUseCase.cancelLobby(new GameSessionId(id), new UserId(req.userId()));
        return ResponseEntity.ok(toDto(session));
    }

    /**
     * Returns the active lobby session (status = WAITING) for the given game
     * machine, if any. Used by clients to discover the session id of an
     * existing lobby so they can join it without relying on MQTT events.
     * Returns 404 if no lobby is active for the game machine.
     */
    @GetMapping("/lobby/active")
    public ResponseEntity<GameSessionDto> getActiveLobby(@RequestParam("gameId") String gameId) {
        return getActiveLobbyUseCase.getActiveLobby(new GameId(gameId))
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Cancels the active lobby session for the given game machine. Used
     * by clients that initiated a lobby create but navigated away before
     * the server's {@code lobby/create} echo arrived (so they don't have
     * the session id to call {@code /{id}/cancel-lobby}). Looks up the
     * active WAITING session by gameId and cancels it.
     *
     * @param gameId the game machine identifier
     * @param req    must contain the creator's userId
     * @return 200 with the cancelled session, or 404 if no active lobby
     */
    @PostMapping("/lobby/cancel-by-game")
    public ResponseEntity<GameSessionDto> cancelLobbyByGame(
            @RequestParam("gameId") String gameId,
            @RequestBody JoinSessionRequestDto req) {
        return getActiveLobbyUseCase.getActiveLobby(new GameId(gameId))
                .map(session -> cancelLobbyUseCase.cancelLobby(session.getId(), new UserId(req.userId())))
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/end")
    public ResponseEntity<Void> end(@PathVariable String id, @RequestBody GameResult result) {
        endGameSessionUseCase.end(new GameSessionId(id), result);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<Void> pause(@PathVariable String id) {
        pauseGameSessionUseCase.pause(new GameSessionId(id));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<Void> resume(@PathVariable String id) {
        resumeGameSessionUseCase.resume(new GameSessionId(id));
        return ResponseEntity.ok().build();
    }

    private GameSessionDto toDto(GameSession session) {
        return getGameSessionDto(session, objectMapper);
    }

    @NonNull
    public static GameSessionDto getGameSessionDto(GameSession session, ObjectMapper objectMapper) {
        String winnerIdStr = session.getWinnerId() != null ? session.getWinnerId().value() : null;
        String resultDataStr = null;
        if (session.getResult() != null) {
            try {
                resultDataStr = objectMapper.writeValueAsString(session.getResult());
            } catch (Exception e) {
                // Ignore serialization error in DTO mapping
            }
        }

        return new GameSessionDto(
                session.getId().value(),
                session.getGameId().id(),
                session.getGameType(),
                session.getStatus(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getDurationSeconds(),
                winnerIdStr,
                session.getWinCondition(),
                resultDataStr,
                session.getParticipants() != null
                        ? session.getParticipants().stream().map(UserId::value).toList()
                        : java.util.List.of()
        );
    }
}
