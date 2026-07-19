package com.gameplatform.central.infrastructure.adapters.in.rest;

import com.gameplatform.central.domain.model.GameDefinition;
import com.gameplatform.central.domain.ports.in.ListGameDefinitionsUseCase;
import com.gameplatform.central.domain.ports.in.UpsertGameDefinitionUseCase;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.GameDefinitionDto;
import com.gameplatform.shared.dto.UpsertGameDefinitionRequestDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * REST adapter for game-definition CRUD exposed to GAME_ADMIN operators
 * (central Source-of-Truth). Per {@code PIANO_UTENTI_TORNEI.md} &sect;1.5,
 * writes ({@code POST}, {@code PUT}) require the {@code GAME_ADMIN} role,
 * while reads ({@code GET}) are available to any authenticated principal.
 *
 * <p>Because the role requirements differ between write and read endpoints,
 * authorization is enforced at the method level rather than at the class
 * level.</p>
 *
 * <p>Exception-to-HTTP-status mapping is delegated to {@link GlobalExceptionHandler}:
 * <ul>
 *   <li>{@code IllegalArgumentException} &rarr; 400 Bad Request</li>
 *   <li>{@code MethodArgumentNotValidException} &rarr; 400 Bad Request</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/admin/games")
public class GameAdminController {

    private final UpsertGameDefinitionUseCase upsertUseCase;
    private final ListGameDefinitionsUseCase listUseCase;
    private final Clock clock;

    /**
     * Costruisce il controller iniettando i casi d'uso e l'orologio di dominio.
     *
     * @param upsertUseCase caso d'uso per creare o aggiornare una definizione di gioco, non {@code null}
     * @param listUseCase   caso d'uso per elencare le definizioni di gioco, non {@code null}
     * @param clock         orologio di dominio per la generazione degli istanti temporali, non {@code null}
     */
    public GameAdminController(UpsertGameDefinitionUseCase upsertUseCase,
                                ListGameDefinitionsUseCase listUseCase,
                                Clock clock) {
        this.upsertUseCase = upsertUseCase;
        this.listUseCase = listUseCase;
        this.clock = clock;
    }

    /**
     * Crea o aggiorna una definizione di gioco a partire dai dati forniti.
     *
     * <p>L'operazione richiede il ruolo {@code GAME_ADMIN} o {@code PLATFORM_ADMIN}
     * e utilizza l'orologio di dominio per marcare gli istanti di creazione e
     * aggiornamento della definizione.</p>
     *
     * @param request dto di richiesta con i dati della definizione, validato tramite {@code @Valid}; non {@code null}
     * @return {@link ResponseEntity} con stato {@code 200 OK} e il {@link GameDefinitionDto} della definizione salvata
     * @throws IllegalArgumentException se i dati forniti violano i vincoli di dominio (mappato a {@code 400})
     * @throws jakarta.validation.ValidationException se il body non supera i vincoli di validazione (mappato a {@code 400})
     * @see GlobalExceptionHandler
     */
    @PostMapping("/definitions")
    @PreAuthorize("hasRole('GAME_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<GameDefinitionDto> upsertDefinition(@Valid @RequestBody UpsertGameDefinitionRequestDto request) {
        Instant now = Instant.now(clock);
        GameDefinition saved = upsertUseCase.upsert(new GameDefinition(
                request.gameType(),
                request.name(),
                request.minPlayers(),
                request.maxPlayers(),
                request.teamAllowed(),
                request.registrationRules(),
                now,
                now));
        return ResponseEntity.ok(toDto(saved));
    }

    /**
     * Aggiorna una definizione di gioco esistente identificata dal tipo di gioco.
     *
     * <p>L'operazione richiede il ruolo {@code GAME_ADMIN} o {@code PLATFORM_ADMIN}.
     * Verifica che il {@code gameType} presente nel path coincida con quello indicato
     * nel body, altrimenti rifiuta la richiesta.</p>
     *
     * @param gameType tipo di gioco da aggiornare, ricavato dal path; non {@code null}
     * @param request  dto di richiesta con i nuovi dati della definizione, validato tramite {@code @Valid}; non {@code null}
     * @return {@link ResponseEntity} con stato {@code 200 OK} e il {@link GameDefinitionDto} della definizione aggiornata
     * @throws IllegalArgumentException se il {@code gameType} del path non coincide con quello del body,
     *                                  o se i dati violano i vincoli di dominio (mappato a {@code 400})
     * @throws jakarta.validation.ValidationException se il body non supera i vincoli di validazione (mappato a {@code 400})
     * @see GlobalExceptionHandler
     */
    @PutMapping("/definitions/{gameType}")
    @PreAuthorize("hasRole('GAME_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<GameDefinitionDto> updateDefinition(@PathVariable GameType gameType,
                                                              @Valid @RequestBody UpsertGameDefinitionRequestDto request) {
        if (request.gameType() != gameType) {
            throw new IllegalArgumentException("Path gameType (" + gameType + ") must match body gameType (" + request.gameType() + ")");
        }
        Instant now = Instant.now(clock);
        GameDefinition saved = upsertUseCase.upsert(new GameDefinition(
                request.gameType(),
                request.name(),
                request.minPlayers(),
                request.maxPlayers(),
                request.teamAllowed(),
                request.registrationRules(),
                now,
                now));
        return ResponseEntity.ok(toDto(saved));
    }

    /**
     * Restituisce l'elenco di tutte le definizioni di gioco presenti nel sistema.
     *
     * <p>L'operazione è disponibile a qualsiasi principal autenticato.</p>
     *
     * @return {@link ResponseEntity} con stato {@code 200 OK} e la lista dei {@link GameDefinitionDto};
     *         la lista è vuota se non esiste alcuna definizione
     */
    @GetMapping("/definitions")
    public ResponseEntity<List<GameDefinitionDto>> listDefinitions() {
        List<GameDefinitionDto> dtos = listUseCase.findAll().stream()
                .map(GameAdminController::toDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Converte un {@link GameDefinition} di dominio nel corrispondente DTO di trasporto.
     *
     * @param def definizione di gioco di dominio da convertire, non {@code null}
     * @return {@link GameDefinitionDto} contenente i dati esposti della definizione
     */
    private static GameDefinitionDto toDto(GameDefinition def) {
        return new GameDefinitionDto(
                def.getGameType(),
                def.getName(),
                def.getMinPlayers(),
                def.getMaxPlayers(),
                def.isTeamAllowed(),
                def.getRegistrationRules());
    }
}