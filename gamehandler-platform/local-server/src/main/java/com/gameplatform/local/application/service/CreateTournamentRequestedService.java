package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.ports.in.CreateTournamentRequestedUseCase;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.AdminRequestDto;
import com.gameplatform.shared.dto.TournamentCreateRequestedEventDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Implementazione del caso d'uso W12a (PIANO §7.B): un PLATFORM_ADMIN
 * crea un nuovo torneo. Esegue il pre-controllo del ruolo
 * {@code PLATFORM_ADMIN} su {@code replicated_users}, quindi scrive
 * atomicamente una riga {@code admin_requests_local} in stato PENDING
 * e il corrispondente evento outbox {@code TOURNAMENT_CREATE_REQUESTED}.
 *
 * @see CreateTournamentRequestedUseCase
 * @see AdminRequestOutboxWriter
 * @see RolePreCheck
 */
@Service
public class CreateTournamentRequestedService implements CreateTournamentRequestedUseCase {

    static final String EVENT_TYPE = "TOURNAMENT_CREATE_REQUESTED";
    static final String REQUIRED_ROLE = "PLATFORM_ADMIN";

    private final UserRepository userRepository;
    private final AdminRequestOutboxWriter outboxWriter;
    private final Clock clock;

    public CreateTournamentRequestedService(UserRepository userRepository,
                                             AdminRequestOutboxWriter outboxWriter,
                                             Clock clock) {
        this.userRepository = userRepository;
        this.outboxWriter = outboxWriter;
        this.clock = clock;
    }

    /**
     * Crea un nuovo torneo. Verifica che l'utente agente possieda il ruolo
     * {@code PLATFORM_ADMIN} e che i parametri siano validi, poi scrive la
     * richiesta admin PENDING e l'evento outbox.
     *
     * @param name         il nome del torneo (non blank)
     * @param gameType     il tipo di gioco del torneo (non null)
     * @param teamBased    true se il torneo e' a squadre, false per individuale
     * @param teamSize     il numero di giocatori per squadra
     * @param startsAt     l'istante di inizio del torneo (non null)
     * @param buildingIds  la lista degli identificativi dei building ospitanti (almeno 2)
     * @param actingUserId l'identificativo dell'utente richiedente
     * @param actingRole   il ruolo con cui l'utente agisce
     * @param buildingId   l'identificativo del building di competenza dell'utente
     * @return il DTO della richiesta admin creata
     * @throws IllegalArgumentException se name e' blank, gameType e' null, startsAt e' null o buildingIds ha meno di 2 elementi
     * @throws org.springframework.security.access.AccessDeniedException se l'utente non ha il ruolo PLATFORM_ADMIN
     */
    @Override
    @Transactional
    public AdminRequestDto create(String name,
                                    GameType gameType,
                                    boolean teamBased,
                                    int teamSize,
                                    Instant startsAt,
                                    List<String> buildingIds,
                                    String actingUserId,
                                    String actingRole,
                                    String buildingId) {
        RolePreCheck.requireRole(userRepository, actingUserId, REQUIRED_ROLE);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        if (gameType == null) {
            throw new IllegalArgumentException("gameType cannot be null");
        }
        if (startsAt == null) {
            throw new IllegalArgumentException("startsAt cannot be null");
        }
        if (buildingIds == null || buildingIds.size() < 2) {
            throw new IllegalArgumentException("buildingIds must contain at least 2 entries");
        }
        Instant now = Instant.now(clock);
        TournamentCreateRequestedEventDto payload = new TournamentCreateRequestedEventDto(
                null, EVENT_TYPE, null, actingUserId, REQUIRED_ROLE, buildingId,
                name, gameType, teamBased, teamSize, startsAt, buildingIds, now
        );
        return outboxWriter.writePendingRequest(EVENT_TYPE, actingUserId, REQUIRED_ROLE, buildingId, payload);
    }
}