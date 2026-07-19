package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.exception.GameNotAvailableException;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.ports.in.GetAvailableGamesUseCase;
import com.gameplatform.local.domain.ports.in.ListBuildingGamesUseCase;
import com.gameplatform.local.domain.ports.in.UpdateGameStateUseCase;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Servizio per la gestione dello stato delle macchine da gioco.
 * Implementa i casi d'uso di aggiornamento stato, lettura dei giochi
 * disponibili e lettura per building. Le transizioni di stato sono
 * applicate tramite i metodi del dominio {@link Game} e le modifiche
 * vengono pubblicate su MQTT. L'aggiornamento e' idempotente: se lo
 * stato non cambia, la persistenza e la pubblicazione MQTT vengono
 * saltate per evitare loop infiniti di eco MQTT.
 *
 * @see UpdateGameStateUseCase
 * @see GetAvailableGamesUseCase
 * @see ListBuildingGamesUseCase
 * @see GameRepository
 * @see PublishGameStatePort
 */
@Service
@Transactional
public class GameStateService implements UpdateGameStateUseCase, GetAvailableGamesUseCase, ListBuildingGamesUseCase {

    private final GameRepository gameRepository;
    private final PublishGameStatePort publishGameStatePort;

    /**
     * Costruisce il servizio con il repository dei giochi e il port di
     * pubblicazione dello stato su MQTT.
     *
     * @param gameRepository        il repository per l'accesso ai dati dei giochi (non null)
     * @param publishGameStatePort  il port per la pubblicazione dello stato su MQTT (non null)
     */
    public GameStateService(GameRepository gameRepository, PublishGameStatePort publishGameStatePort) {
        this.gameRepository = gameRepository;
        this.publishGameStatePort = publishGameStatePort;
    }

    /**
     * Aggiorna lo stato di una macchina da gioco. Applica la transizione
     * di stato tramite i metodi del dominio, salta la persistenza e la
     * pubblicazione MQTT se lo stato risultante e' identico al precedente
     * (idempotenza, evita loop di eco MQTT).
     *
     * @param gameId    l'identificativo del gioco
     * @param newStatus il nuovo stato da applicare
     * @throws GameNotAvailableException se il gioco non viene trovato
     */
    @Override
    public void updateState(GameId gameId, GameMachineStatus newStatus) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new GameNotAvailableException("Game machine not found: " + gameId.id()));

        GameMachineStatus previousStatus = game.getStatus();

        // Enforce Clean Architecture state validation via domain methods
        switch (newStatus) {
            case AVAILABLE -> game.release();
            case RESERVED -> game.reserve();
            case IN_USE -> game.startUse();
            case MAINTENANCE -> game.setMaintenance();
        }

        // Idempotency: skip persistence and MQTT re-publish when the status is
        // unchanged. The local-server is subscribed to the same
        // building/{id}/game/+/state topic it publishes to, so echoing a
        // no-op transition (e.g. AVAILABLE -> AVAILABLE, since Game.release()
        // returns silently when already AVAILABLE) would otherwise cause an
        // infinite MQTT echo loop hammering the database.
        if (game.getStatus() == previousStatus) {
            return;
        }

        gameRepository.save(game);
        publishGameStatePort.publishState(gameId, game.getStatus());
    }

    /**
     * Restituisce la lista di tutti i giochi disponibili (stato AVAILABLE).
     *
     * @return la lista dei giochi disponibili
     */
    @Override
    public List<Game> getAvailable() {
        return gameRepository.findByStatus(GameMachineStatus.AVAILABLE);
    }

    /**
     * Restituisce la lista completa di tutti i giochi.
     *
     * @return la lista di tutti i giochi
     */
    @Override
    public List<Game> getAll() {
        return gameRepository.findAll();
    }

    /**
     * Restituisce la lista dei giochi appartenenti a un building specifico.
     *
     * @param buildingId l'identificativo del building
     * @return la lista dei giochi del building
     */
    @Override
    @Transactional(readOnly = true)
    public List<Game> getByBuilding(BuildingId buildingId) {
        return gameRepository.findByBuildingId(buildingId);
    }
}
