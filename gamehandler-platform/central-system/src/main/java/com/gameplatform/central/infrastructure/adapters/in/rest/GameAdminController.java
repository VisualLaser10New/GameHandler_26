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

    public GameAdminController(UpsertGameDefinitionUseCase upsertUseCase,
                                ListGameDefinitionsUseCase listUseCase,
                                Clock clock) {
        this.upsertUseCase = upsertUseCase;
        this.listUseCase = listUseCase;
        this.clock = clock;
    }

    @PostMapping("/definitions")
    @PreAuthorize("hasRole('GAME_ADMIN')")
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

    @PutMapping("/definitions/{gameType}")
    @PreAuthorize("hasRole('GAME_ADMIN')")
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

    @GetMapping("/definitions")
    public ResponseEntity<List<GameDefinitionDto>> listDefinitions() {
        List<GameDefinitionDto> dtos = listUseCase.findAll().stream()
                .map(GameAdminController::toDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

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