package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.exception.BuildingNotRegisteredToAdminException;
import com.gameplatform.local.domain.exception.GameDefinitionNotAvailableLocallyException;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.model.LocalStatistics;
import com.gameplatform.local.domain.ports.in.GetBuildingStatisticsUseCase;
import com.gameplatform.local.domain.ports.in.ListBuildingActiveSessionsUseCase;
import com.gameplatform.local.domain.ports.in.ListBuildingGamesUseCase;
import com.gameplatform.local.domain.ports.in.ManageGameCatalogUseCase;
import com.gameplatform.local.domain.ports.out.GameDefinitionLocalRepository;
import com.gameplatform.local.infrastructure.security.LocalAdminBuildingAuthorizationManager;
import com.gameplatform.shared.domain.game.GameFactory;
import com.gameplatform.shared.domain.game.GameLifecycle;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.CreateGameRequestDto;
import com.gameplatform.shared.dto.GameStateDto;
import com.gameplatform.shared.dto.GameSessionDto;
import com.gameplatform.shared.dto.UpdateGameRequestDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import java.util.Arrays;
import java.util.List;

/**
 * LOCAL_ADMIN REST endpoints for managing this building's game catalog and
 * viewing building-scoped sessions/statistics. The class-level
 * {@link PreAuthorize} enforces the {@code LOCAL_ADMIN} role; per-request
 * building enforcement is delegated to
 * {@link LocalAdminBuildingAuthorizationManager#canManageBuilding} (A3 — no
 * JWT {@code buildings} claim; the binding is read from the replicated
 * {@code local_admin_buildings_local} table).
 */
@RestController
@RequestMapping("/api/admin/local")
@PreAuthorize("hasRole('LOCAL_ADMIN') or hasRole('PLATFORM_ADMIN')")
public class AdminLocalController {

    private final ManageGameCatalogUseCase manageGameCatalogUseCase;
    private final ListBuildingGamesUseCase listBuildingGamesUseCase;
    private final ListBuildingActiveSessionsUseCase listBuildingActiveSessionsUseCase;
    private final GetBuildingStatisticsUseCase getBuildingStatisticsUseCase;
    private final ObjectMapper objectMapper;
    private final LocalAdminBuildingAuthorizationManager authorizationManager;
    private final String buildingId;
    private final GameDefinitionLocalRepository gameDefinitionLocalRepository;

    /**
     * Costruisce il controller con i casi d'uso necessari per la gestione del
     * catalogo giochi, la consultazione delle sessioni e delle statistiche
     * relative all'edificio.
     *
     * @param manageGameCatalogUseCase caso d'uso per la gestione del catalogo giochi
     * @param listBuildingGamesUseCase caso d'uso per la lista dei giochi dell'edificio
     * @param listBuildingActiveSessionsUseCase caso d'uso per le sessioni attive dell'edificio
     * @param getBuildingStatisticsUseCase caso d'uso per le statistiche dell'edificio
     * @param objectMapper mapper JSON per la serializzazione
     * @param authorizationManager gestore delle autorizzazioni per l'admin locale
     * @param buildingId identificativo dell'edificio
     * @param gameDefinitionLocalRepository repository locale delle definizioni di gioco
     */
    public AdminLocalController(ManageGameCatalogUseCase manageGameCatalogUseCase,
                                ListBuildingGamesUseCase listBuildingGamesUseCase,
                                ListBuildingActiveSessionsUseCase listBuildingActiveSessionsUseCase,
                                GetBuildingStatisticsUseCase getBuildingStatisticsUseCase,
                                ObjectMapper objectMapper,
                                LocalAdminBuildingAuthorizationManager authorizationManager,
                                @Value("${app.building-id}") String buildingId,
                                GameDefinitionLocalRepository gameDefinitionLocalRepository) {
        this.manageGameCatalogUseCase = manageGameCatalogUseCase;
        this.listBuildingGamesUseCase = listBuildingGamesUseCase;
        this.listBuildingActiveSessionsUseCase = listBuildingActiveSessionsUseCase;
        this.getBuildingStatisticsUseCase = getBuildingStatisticsUseCase;
        this.objectMapper = objectMapper;
        this.authorizationManager = authorizationManager;
        this.buildingId = buildingId;
        this.gameDefinitionLocalRepository = gameDefinitionLocalRepository;
    }

    /**
     * Restituisce l'elenco dei dispositivi (giochi) associati all'edificio
     * dell'amministratore locale autenticato.
     *
     * @return una {@link ResponseEntity} contenente la lista di {@link GameStateDto}
     * @throws BuildingNotRegisteredToAdminException se l'admin non è autorizzato per l'edificio
     * @see #ensureAuthorized()
     */
    @GetMapping("/devices")
    public ResponseEntity<List<GameStateDto>> getDevices() {
        ensureAuthorized();
        List<Game> games = listBuildingGamesUseCase.getByBuilding(new BuildingId(buildingId));
        List<GameStateDto> dtos = games.stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Restituisce le sessioni di gioco attive per l'edificio dell'amministratore
     * locale autenticato.
     *
     * @return una {@link ResponseEntity} contenente la lista di {@link GameSessionDto}
     * @throws BuildingNotRegisteredToAdminException se l'admin non è autorizzato per l'edificio
     */
    @GetMapping("/sessions/active")
    public ResponseEntity<List<GameSessionDto>> getActiveSessions() {
        ensureAuthorized();
        List<GameSession> sessions = listBuildingActiveSessionsUseCase
                .getActiveSessionsByBuilding(new BuildingId(buildingId));
        List<GameSessionDto> dtos = sessions.stream()
                .map(s -> GameSessionController.getGameSessionDto(s, objectMapper))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Restituisce le statistiche per un determinato tipo di gioco relative
     * all'edificio dell'amministratore locale autenticato.
     *
     * @param gameTypeStr il tipo di gioco come stringa (obbligatorio)
     * @return una {@link ResponseEntity} contenente le {@link LocalStatistics}
     * @throws IllegalArgumentException se il parametro gameType è nullo, vuoto o non valido
     * @throws BuildingNotRegisteredToAdminException se l'admin non è autorizzato per l'edificio
     */
    @GetMapping("/statistics")
    public ResponseEntity<LocalStatistics> getStatistics(@RequestParam(value = "gameType", required = false) String gameTypeStr) {
        ensureAuthorized();
        if (gameTypeStr == null || gameTypeStr.isBlank()) {
            throw new IllegalArgumentException("gameType query parameter is required");
        }
        GameType gameType = parseGameType(gameTypeStr);
        LocalStatistics statistics = getBuildingStatisticsUseCase
                .getStatisticsForBuilding(gameType, new BuildingId(buildingId));
        return ResponseEntity.ok(statistics);
    }

    /**
     * Crea un nuovo gioco nel catalogo dell'edificio.
     *
     * @param req i dati della richiesta di creazione del gioco
     * @return una {@link ResponseEntity} con status 201 e il {@link GameStateDto} del gioco creato
     * @throws GameDefinitionNotAvailableLocallyException se la definizione del gioco non è disponibile localmente
     * @throws BuildingNotRegisteredToAdminException se l'admin non è autorizzato per l'edificio
     */
    @PostMapping("/games")
    public ResponseEntity<GameStateDto> createGame(@RequestBody @Valid CreateGameRequestDto req) {
        ensureAuthorized();
        GameType gameType = parseGameType(req.gameType());
        if (!gameDefinitionLocalRepository.existsByGameType(gameType)) {
            throw new GameDefinitionNotAvailableLocallyException(
                    "GameDefinition for type " + gameType + " is not available locally; ensure central replication has reached this building");
        }
        Game created = manageGameCatalogUseCase.createGame(gameType, req.name(), new BuildingId(buildingId));
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(created));
    }

    /**
     * Aggiorna un gioco esistente nel catalogo dell'edificio.
     *
     * @param gameId l'identificativo del gioco da aggiornare
     * @param req i dati della richiesta di aggiornamento
     * @return una {@link ResponseEntity} contenente il {@link GameStateDto} aggiornato
     * @throws BuildingNotRegisteredToAdminException se l'admin non è autorizzato per l'edificio
     */
    @PutMapping("/games/{gameId}")
    public ResponseEntity<GameStateDto> updateGame(@PathVariable String gameId,
                                                   @RequestBody @Valid UpdateGameRequestDto req) {
        ensureAuthorized();
        GameMachineStatus newStatus = null;
        if (req.status() != null && !req.status().isBlank()) {
            newStatus = parseStatus(req.status());
        }
        Game updated = manageGameCatalogUseCase.updateGame(new GameId(gameId), req.name(), newStatus);
        return ResponseEntity.ok(toDto(updated));
    }

    /**
     * Elimina un gioco dal catalogo dell'edificio.
     *
     * @param gameId l'identificativo del gioco da eliminare
     * @return una {@link ResponseEntity} con status 204 (nessun contenuto)
     * @throws BuildingNotRegisteredToAdminException se l'admin non è autorizzato per l'edificio
     */
    @DeleteMapping("/games/{gameId}")
    public ResponseEntity<Void> deleteGame(@PathVariable String gameId) {
        ensureAuthorized();
        manageGameCatalogUseCase.deleteGame(new GameId(gameId));
        return ResponseEntity.noContent().build();
    }

    /**
     * Verifica che l'amministratore autenticato sia autorizzato a gestire
     * l'edificio corrente. Solleva un'eccezione in caso contrario.
     *
     * @throws BuildingNotRegisteredToAdminException se l'admin non è associato all'edificio
     */
    private void ensureAuthorized() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!authorizationManager.canManageBuilding(authentication)) {
            throw new BuildingNotRegisteredToAdminException(
                    "Local admin is not authorized to manage building " + buildingId);
        }
    }

    /**
     * Converte una stringa nel corrispondente enum {@link GameType}.
     *
     * @param gameType la stringa rappresentante il tipo di gioco
     * @return il {@link GameType} corrispondente
     * @throws IllegalArgumentException se la stringa non corrisponde a un valore valido
     */
    private static GameType parseGameType(String gameType) {
        try {
            return GameType.valueOf(gameType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown game type: '" + gameType + "'. Valid values are: "
                            + Arrays.toString(GameType.values()));
        }
    }

    /**
     * Converte una stringa nel corrispondente enum {@link GameMachineStatus}.
     *
     * @param status la stringa rappresentante lo stato della macchina
     * @return il {@link GameMachineStatus} corrispondente
     * @throws IllegalArgumentException se la stringa non corrisponde a un valore valido
     */
    private static GameMachineStatus parseStatus(String status) {
        try {
            return GameMachineStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown status: '" + status + "'. Valid values are: "
                            + Arrays.toString(GameMachineStatus.values()));
        }
    }

    /**
     * Proietta un'istanza di {@link Game} in un {@link GameStateDto},
     * risolvendo il numero minimo e massimo di giocatori dalla definizione
     * locale o, in assenza, dai valori predefiniti del ciclo di vita del gioco.
     *
     * @param game il gioco da convertire
     * @return il {@link GameStateDto} corrispondente
     */
    private GameStateDto toDto(Game game) {
        com.gameplatform.local.domain.model.GameDefinitionLocal def =
                gameDefinitionLocalRepository.findByGameType(game.getGameType()).orElse(null);
        int minPlayers;
        int maxPlayers;
        if (def != null) {
            minPlayers = def.getMinPlayers();
            maxPlayers = def.getMaxPlayers();
        } else {
            GameLifecycle lifecycle = GameFactory.createGame(game.getGameType(), null);
            minPlayers = lifecycle.getMinPlayers();
            maxPlayers = lifecycle.getMaxPlayers();
        }
        return new GameStateDto(
                game.getId().id(),
                game.getGameType(),
                game.getName(),
                game.getBuildingId().id(),
                game.getStatus(),
                minPlayers,
                maxPlayers
        );
    }
}