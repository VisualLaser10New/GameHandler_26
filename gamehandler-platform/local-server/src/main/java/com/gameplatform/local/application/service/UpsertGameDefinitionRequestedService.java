package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.ports.in.UpsertGameDefinitionRequestedUseCase;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.AdminRequestDto;
import com.gameplatform.shared.dto.GameDefinitionUpsertRequestedEventDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * Implementazione del caso d'uso W9 (PIANO §7.B): un GAME_ADMIN crea o
 * aggiorna una definizione di gioco. Esegue il pre-controllo del ruolo
 * {@code GAME_ADMIN} su {@code replicated_users}, poi scrive atomicamente
 * una riga {@code admin_requests_local} PENDING e l'evento outbox
 * {@code GAME_DEFINITION_UPSERT_REQUESTED}.
 *
 * @see UpsertGameDefinitionRequestedUseCase
 * @see AdminRequestOutboxWriter
 * @see RolePreCheck
 */
@Service
public class UpsertGameDefinitionRequestedService
        implements UpsertGameDefinitionRequestedUseCase {

    static final String EVENT_TYPE = "GAME_DEFINITION_UPSERT_REQUESTED";
    static final String REQUIRED_ROLE = "GAME_ADMIN";

    private final UserRepository userRepository;
    private final AdminRequestOutboxWriter outboxWriter;
    private final Clock clock;

    public UpsertGameDefinitionRequestedService(UserRepository userRepository,
                                                  AdminRequestOutboxWriter outboxWriter,
                                                  Clock clock) {
        this.userRepository = userRepository;
        this.outboxWriter = outboxWriter;
        this.clock = clock;
    }

    /**
     * Crea o aggiorna una definizione di gioco. Verifica il ruolo
     * GAME_ADMIN e la validita' dei parametri, poi scrive la richiesta
     * admin PENDING e l'evento outbox.
     *
     * @param gameType          il tipo di gioco (non null)
     * @param name              il nome della definizione (non blank)
     * @param minPlayers        il numero minimo di giocatori (almeno 1)
     * @param maxPlayers        il numero massimo di giocatori (almeno minPlayers)
     * @param teamAllowed       true se il gioco supporta squadre
     * @param registrationRules regole di registrazione (mappa chiave-valore)
     * @param actingUserId      l'identificativo dell'utente richiedente
     * @param actingRole        il ruolo con cui l'utente agisce
     * @param buildingId        l'identificativo del building di competenza
     * @return il DTO della richiesta admin creata
     * @throws IllegalArgumentException se i parametri non sono validi
     * @throws org.springframework.security.access.AccessDeniedException se l'utente non ha il ruolo GAME_ADMIN
     */
    @Override
    @Transactional
    public AdminRequestDto upsert(GameType gameType,
                                    String name,
                                    int minPlayers,
                                    int maxPlayers,
                                    boolean teamAllowed,
                                    Map<String, Object> registrationRules,
                                    String actingUserId,
                                    String actingRole,
                                    String buildingId) {
        RolePreCheck.requireRole(userRepository, actingUserId, REQUIRED_ROLE);
        if (gameType == null) {
            throw new IllegalArgumentException("gameType cannot be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        if (minPlayers < 1 || maxPlayers < 1 || minPlayers > maxPlayers) {
            throw new IllegalArgumentException("invalid player count range");
        }
        Instant now = Instant.now(clock);
        GameDefinitionUpsertRequestedEventDto payload = new GameDefinitionUpsertRequestedEventDto(
                null, EVENT_TYPE, null, actingUserId, REQUIRED_ROLE, buildingId,
                gameType, name, minPlayers, maxPlayers, teamAllowed, registrationRules, now
        );
        return outboxWriter.writePendingRequest(EVENT_TYPE, actingUserId, REQUIRED_ROLE, buildingId, payload);
    }
}