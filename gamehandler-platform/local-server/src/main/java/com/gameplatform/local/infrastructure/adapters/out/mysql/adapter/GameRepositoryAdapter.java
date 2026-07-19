package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.GameJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.GameMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.GameJpaRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adapter JPA per il port {@link GameRepository}.
 * Gestisce la persistenza delle entità {@link Game} nel database MySQL,
 * con gestione dei conflitti di concorrenza tramite {@link OptimisticLockingFailureException}
 * convertita in {@link ConcurrentStateException}.
 *
 * @see GameRepository
 * @see GameJpaRepository
 * @see com.gameplatform.local.domain.exception.ConcurrentStateException
 */
@Component
public class GameRepositoryAdapter implements GameRepository {

    private final GameJpaRepository jpaRepository;
    private final GameMapper mapper;

    /**
     * Costruisce un nuovo adapter con le dipendenze necessarie.
     *
     * @param jpaRepository repository JPA per le entità gioco
     * @param mapper        mapper per la conversione tra entity e dominio
     */
    public GameRepositoryAdapter(GameJpaRepository jpaRepository, GameMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    /**
     * Salva un gioco nel database con flush immediato.
     * In caso di conflitto di versione, lancia una {@link ConcurrentStateException}.
     *
     * @param game il gioco da salvare
     * @return il gioco persistito
     * @throws com.gameplatform.local.domain.exception.ConcurrentStateException in caso di modifica concorrente
     */
    @Override
    public Game save(Game game) {
        GameJpaEntity entity = mapper.toEntity(game);
        try {
            GameJpaEntity saved = jpaRepository.saveAndFlush(entity);
            return mapper.toDomain(saved);
        } catch (org.springframework.dao.OptimisticLockingFailureException ex) {
            throw new com.gameplatform.local.domain.exception.ConcurrentStateException(
                "Concurrent modification of game " + game.getId().id(), ex);
        }
    }

    /**
     * Recupera un gioco tramite il suo identificativo.
     *
     * @param id l'identificativo del gioco
     * @return un {@code Optional} contenente il gioco, vuoto se non trovato
     */
    @Override
    public Optional<Game> findById(GameId id) {
        return jpaRepository.findById(id.id()).map(mapper::toDomain);
    }

    /**
     * Recupera un gioco tramite identificativo con blocco pessimistico per aggiornamento.
     *
     * @param id l'identificativo del gioco
     * @return un {@code Optional} contenente il gioco bloccato, vuoto se non trovato
     */
    @Override
    public Optional<Game> findByIdForUpdate(GameId id) {
        return jpaRepository.findByIdForUpdate(id.id()).map(mapper::toDomain);
    }

    /**
     * Recupera tutti i giochi associati a un dato edificio.
     *
     * @param buildingId l'identificativo dell'edificio
     * @return una lista di giochi associati all'edificio
     */
    @Override
    public List<Game> findByBuildingId(BuildingId buildingId) {
        return jpaRepository.findByBuildingId(buildingId.id()).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    /**
     * Recupera tutti i giochi con un dato stato macchina.
     *
     * @param status lo stato macchina da filtrare
     * @return una lista di giochi con lo stato specificato
     */
    @Override
    public List<Game> findByStatus(GameMachineStatus status) {
        return jpaRepository.findByStatus(status).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    /**
     * Recupera tutti i giochi presenti nel database.
     *
     * @return una lista completa di tutti i giochi
     */
    @Override
    public List<Game> findAll() {
        return jpaRepository.findAll().stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    /**
     * Elimina un gioco tramite il suo identificativo.
     *
     * @param id l'identificativo del gioco da eliminare; se {@code null} l'operazione non viene eseguita
     */
    @Override
    @Transactional
    public void deleteById(GameId id) {
        if (id == null) {
            return;
        }
        jpaRepository.deleteById(id.id());
    }
}
