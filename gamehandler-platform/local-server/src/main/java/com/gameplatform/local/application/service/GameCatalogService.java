package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.exception.GameNotAvailableException;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.ports.in.ManageGameCatalogUseCase;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.domain.model.GameType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Servizio applicativo che implementa le operazioni CRUD sul catalogo
 * giochi per l'admin locale (endpoint {@code POST/PUT/DELETE /api/admin/local/games}).
 * Applica gli invarianti di transizione di stato tramite i metodi del
 * dominio {@link Game} e utilizza il lock pessimistico
 * {@link GameRepository#findByIdForUpdate} per l'aggiornamento.
 *
 * @see ManageGameCatalogUseCase
 * @see GameRepository
 * @see GameStateService
 */
@Service
@Transactional
public class GameCatalogService implements ManageGameCatalogUseCase {

    private final GameRepository gameRepository;

    /**
     * Costruisce il servizio con il repository dei giochi.
     *
     * @param gameRepository il repository per l'accesso ai dati dei giochi (non null)
     */
    public GameCatalogService(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

    /**
     * Crea un nuovo gioco nel catalogo con stato AVAILABLE.
     *
     * @param gameType   il tipo di gioco (non null)
     * @param name       il nome del gioco (non blank)
     * @param buildingId l'identificativo del building a cui appartiene (non null)
     * @return il gioco creato
     * @throws IllegalArgumentException se uno dei parametri obbligatori e' null o blank
     */
    @Override
    public Game createGame(GameType gameType, String name, BuildingId buildingId) {
        if (gameType == null) {
            throw new IllegalArgumentException("GameType cannot be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be null, empty or blank");
        }
        if (buildingId == null) {
            throw new IllegalArgumentException("BuildingId cannot be null");
        }
        GameId id = new GameId(UUID.randomUUID().toString());
        Game game = new Game(id, gameType, name, buildingId, GameMachineStatus.AVAILABLE);
        return gameRepository.save(game);
    }

    /**
     * Aggiorna il nome e/o lo stato di un gioco esistente. Utilizza il
     * lock pessimistico {@code findByIdForUpdate} per garantire la
     * consistenza. L'admin puo' impostare solo gli stati AVAILABLE o
     * MAINTENANCE.
     *
     * @param gameId    l'identificativo del gioco da aggiornare (non null)
     * @param newName   il nuovo nome (opzionale, se null o blank non viene aggiornato)
     * @param newStatus il nuovo stato (opzionale, se null non viene aggiornato)
     * @return il gioco aggiornato
     * @throws IllegalArgumentException se gameId e' null, il gioco non esiste,
     *                                  o newStatus non e' AVAILABLE/MAINTENANCE
     */
    @Override
    public Game updateGame(GameId gameId, String newName, GameMachineStatus newStatus) {
        if (gameId == null) {
            throw new IllegalArgumentException("GameId cannot be null");
        }
        boolean hasName = newName != null && !newName.isBlank();
        boolean hasStatus = newStatus != null;
        if (!hasName && !hasStatus) {
            throw new IllegalArgumentException("At least one of name or status must be provided");
        }
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found: " + gameId.id()));
        if (hasName) {
            game.rename(newName);
        }
        if (hasStatus) {
            if (newStatus == GameMachineStatus.MAINTENANCE) {
                game.setMaintenance();
            } else if (newStatus == GameMachineStatus.AVAILABLE) {
                game.release();
            } else {
                throw new IllegalArgumentException(
                        "Admin can only set status to AVAILABLE or MAINTENANCE, got: " + newStatus);
            }
        }
        return gameRepository.save(game);
    }

    /**
     * Elimina un gioco dal catalogo. Non e' consentito eliminare un gioco
     * in stato IN_USE.
     *
     * @param gameId l'identificativo del gioco da eliminare (non null)
     * @throws IllegalArgumentException se gameId e' null o il gioco non esiste
     * @throws GameNotAvailableException se il gioco e' in stato IN_USE
     */
    @Override
    public void deleteGame(GameId gameId) {
        if (gameId == null) {
            throw new IllegalArgumentException("GameId cannot be null");
        }
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found: " + gameId.id()));
        if (game.getStatus() == GameMachineStatus.IN_USE) {
            throw new GameNotAvailableException(
                    "Cannot delete game " + gameId.id() + " while IN_USE");
        }
        gameRepository.deleteById(gameId);
    }
}