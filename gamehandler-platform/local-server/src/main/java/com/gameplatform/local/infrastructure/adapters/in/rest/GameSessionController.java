package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.application.service.GameSessionService;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.model.User;
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
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.dto.JoinSessionRequestDto;
import java.util.List;

/**
 * Controller REST per la gestione delle sessioni di gioco e delle lobby.
 * Espone endpoint per avviare, terminare, mettere in pausa e riprendere
 * sessioni, nonché per creare, unirsi, avviare e cancellare lobby.
 *
 * @see GameSessionService
 * @see StartGameSessionUseCase
 * @see CreateLobbyUseCase
 */
@RestController
@RequestMapping("/api/sessions")
@PreAuthorize("hasRole('PLAYER') or hasRole('PLATFORM_ADMIN')")
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
    private final UserRepository userRepository;

    /**
     * Costruisce il controller con tutti i casi d'uso per la gestione delle
     * sessioni di gioco e delle lobby.
     *
     * @param startGameSessionUseCase caso d'uso per avviare una sessione
     * @param gameSessionService servizio di sessione di gioco (overload 5-arg)
     * @param endGameSessionUseCase caso d'uso per terminare una sessione
     * @param pauseGameSessionUseCase caso d'uso per mettere in pausa una sessione
     * @param resumeGameSessionUseCase caso d'uso per riprendere una sessione
     * @param createLobbyUseCase caso d'uso per creare una lobby
     * @param joinLobbyUseCase caso d'uso per unirsi a una lobby
     * @param startLobbyUseCase caso d'uso per avviare una lobby
     * @param cancelLobbyUseCase caso d'uso per cancellare una lobby
     * @param getActiveLobbyUseCase caso d'uso per ottenere la lobby attiva
     * @param objectMapper mapper JSON per la serializzazione
     * @param userRepository repository degli utenti per la risoluzione dei nomi
     */
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
            ObjectMapper objectMapper,
            UserRepository userRepository) {
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
        this.userRepository = userRepository;
    }

    /**
     * Avvia una nuova sessione di gioco, supportando opzionalmente
     * un ID di prenotazione e un ID di partita torneo.
     *
     * @param req la richiesta contenente i dati di avvio sessione
     * @return una {@link ResponseEntity} con status 201 e il {@link GameSessionDto}
     */
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

    /**
     * Crea una nuova lobby per una partita, con il creatore come primo partecipante.
     *
     * @param req la richiesta contenente i dati della lobby
     * @return una {@link ResponseEntity} con status 201 e il {@link GameSessionDto}
     */
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

    /**
     * Consente a un giocatore di unirsi a una lobby esistente.
     *
     * @param id l'identificativo della sessione (lobby)
     * @param req la richiesta contenente l'ID dell'utente
     * @return una {@link ResponseEntity} con il {@link GameSessionDto} aggiornato
     */
    @PostMapping("/{id}/join")
    public ResponseEntity<GameSessionDto> join(@PathVariable String id, @RequestBody JoinSessionRequestDto req) {
        GameSession session = joinLobbyUseCase.joinLobby(new GameSessionId(id), new UserId(req.userId()));
        return ResponseEntity.ok(toDto(session));
    }

    /**
     * Avvia una lobby, portando la sessione dallo stato WAITING a IN_PROGRESS.
     *
     * @param id l'identificativo della sessione
     * @return una {@link ResponseEntity} con il {@link GameSessionDto} aggiornato
     */
    @PostMapping("/{id}/start-lobby")
    public ResponseEntity<GameSessionDto> startLobby(@PathVariable String id) {
        GameSession session = startLobbyUseCase.startLobby(new GameSessionId(id));
        return ResponseEntity.ok(toDto(session));
    }

    /**
     * Cancella una lobby esistente.
     *
     * @param id l'identificativo della sessione
     * @param req la richiesta contenente l'ID dell'utente che richiede la cancellazione
     * @return una {@link ResponseEntity} con il {@link GameSessionDto} cancellato
     */
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
                .map(this::toLobbyDisplayDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Maps the active lobby session to a {@link GameSessionDto} whose
     * {@code participants} list carries the players' display usernames
     * instead of the canonical user ids / UUIDs stored on the session.
     * Used only by {@code GET /api/sessions/lobby/active}: the joining
     * Game Client Emulator renders the lobby roster from this list (and
     * later feeds the same strings to the in-match scoreboard and per-game
     * panels), so the lobby and scoreboard show names — mirroring the
     * client-side {@code displayName()} resolution of the tournament flow.
     * The canonical ids stay stored on the {@link GameSession} and flow
     * to the outbox / Central player read-models unchanged (see
     * {@code GameSessionService#resolveCanonicalUserId}). Values not
     * replicated locally (team-ids, transient usernames, ids missing
     * from {@code replicated_users}) are kept verbatim, so the resolution
     * is idempotent and never degrades the historical behaviour.
     */
    private GameSessionDto toLobbyDisplayDto(GameSession session) {
        GameSessionDto dto = getGameSessionDto(session, objectMapper);
        List<String> displayNames = resolveParticipantDisplayNames(session.getParticipants());
        return new GameSessionDto(
                dto.id(), dto.gameId(), dto.gameType(), dto.status(),
                dto.startedAt(), dto.endedAt(), dto.durationSeconds(),
                dto.winnerId(), dto.winCondition(), dto.resultData(),
                displayNames);
    }

    /**
     * Risolve i nomi visualizzati dei partecipanti, cercando il nome utente
     * nel repository locale. I partecipanti non trovati vengono mantenuti
     * con il loro valore originale.
     *
     * @param participants la lista degli ID dei partecipanti
     * @return la lista dei nomi visualizzati
     */
    private List<String> resolveParticipantDisplayNames(List<UserId> participants) {
        if (participants == null || participants.isEmpty()) {
            return List.of();
        }
        return participants.stream()
                .filter(p -> p != null && p.value() != null && !p.value().isBlank())
                .map(p -> userRepository.findById(p)
                        .map(User::getUsername)
                        .orElse(p.value()))
                .toList();
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

    /**
     * Termina una sessione di gioco con il risultato fornito.
     *
     * @param id l'identificativo della sessione
     * @param result il risultato della partita
     * @return una {@link ResponseEntity} con status 200
     */
    @PostMapping("/{id}/end")
    public ResponseEntity<Void> end(@PathVariable String id, @RequestBody GameResult result) {
        endGameSessionUseCase.end(new GameSessionId(id), result);
        return ResponseEntity.ok().build();
    }

    /**
     * Mette in pausa una sessione di gioco attiva.
     *
     * @param id l'identificativo della sessione
     * @return una {@link ResponseEntity} con status 200
     */
    @PostMapping("/{id}/pause")
    public ResponseEntity<Void> pause(@PathVariable String id) {
        pauseGameSessionUseCase.pause(new GameSessionId(id));
        return ResponseEntity.ok().build();
    }

    /**
     * Riprende una sessione di gioco in pausa.
     *
     * @param id l'identificativo della sessione
     * @return una {@link ResponseEntity} con status 200
     */
    @PostMapping("/{id}/resume")
    public ResponseEntity<Void> resume(@PathVariable String id) {
        resumeGameSessionUseCase.resume(new GameSessionId(id));
        return ResponseEntity.ok().build();
    }

    /**
     * Converte una sessione di gioco nel corrispondente DTO.
     *
     * @param session la sessione di gioco
     * @return il {@link GameSessionDto} corrispondente
     */
    private GameSessionDto toDto(GameSession session) {
        return getGameSessionDto(session, objectMapper);
    }

    /**
     * Metodo statico di utilità che proietta un {@link GameSession} in un
     * {@link GameSessionDto}, serializzando il risultato come JSON.
     *
     * @param session la sessione di gioco da convertire
     * @param objectMapper mapper JSON per la serializzazione del risultato
     * @return il {@link GameSessionDto} corrispondente
     */
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
