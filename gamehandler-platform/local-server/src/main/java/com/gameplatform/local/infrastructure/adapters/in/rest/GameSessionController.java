package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.ports.in.EndGameSessionUseCase;
import com.gameplatform.local.domain.ports.in.PauseGameSessionUseCase;
import com.gameplatform.local.domain.ports.in.ResumeGameSessionUseCase;
import com.gameplatform.local.domain.ports.in.StartGameSessionUseCase;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.ReservationId;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.result.GameResult;
import com.gameplatform.shared.dto.CreateSessionRequestDto;
import com.gameplatform.shared.dto.GameSessionDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
@PreAuthorize("hasRole('USER')")
public class GameSessionController {

    private final StartGameSessionUseCase startGameSessionUseCase;
    private final EndGameSessionUseCase endGameSessionUseCase;
    private final PauseGameSessionUseCase pauseGameSessionUseCase;
    private final ResumeGameSessionUseCase resumeGameSessionUseCase;
    private final ObjectMapper objectMapper;

    public GameSessionController(
            StartGameSessionUseCase startGameSessionUseCase,
            EndGameSessionUseCase endGameSessionUseCase,
            PauseGameSessionUseCase pauseGameSessionUseCase,
            ResumeGameSessionUseCase resumeGameSessionUseCase,
            ObjectMapper objectMapper) {
        this.startGameSessionUseCase = startGameSessionUseCase;
        this.endGameSessionUseCase = endGameSessionUseCase;
        this.pauseGameSessionUseCase = pauseGameSessionUseCase;
        this.resumeGameSessionUseCase = resumeGameSessionUseCase;
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

        GameSession session = startGameSessionUseCase.start(
                new GameId(req.gameId()),
                req.gameType(),
                participants,
                reservationId
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(session));
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
                resultDataStr
        );
    }
}
