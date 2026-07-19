package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.model.GameDefinitionLocal;
import com.gameplatform.local.domain.ports.out.GameDefinitionLocalRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.GameDefinitionLocalJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.GameDefinitionLocalMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.GameDefinitionLocalJpaRepository;
import com.gameplatform.shared.domain.model.GameType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adapter JPA per il port {@link GameDefinitionLocalRepository}.
 * Gestisce la persistenza delle definizioni dei giochi locali,
 * con operazioni di upsert per chiave primaria {@code game_type}
 * che garantiscono idempotenza in caso di riapplicazione dello
 * stesso snapshot di definizione del gioco.
 *
 * @see GameDefinitionLocalRepository
 * @see GameDefinitionLocalJpaRepository
 */
@Component
public class GameDefinitionLocalRepositoryAdapter implements GameDefinitionLocalRepository {

    private final GameDefinitionLocalJpaRepository jpaRepository;
    private final GameDefinitionLocalMapper mapper;

    /**
     * Costruisce un nuovo adapter con le dipendenze necessarie.
     *
     * @param jpaRepository repository JPA per le definizioni dei giochi
     * @param mapper        mapper per la conversione tra entity e dominio
     */
    public GameDefinitionLocalRepositoryAdapter(GameDefinitionLocalJpaRepository jpaRepository,
                                                 GameDefinitionLocalMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    /**
     * Salva una definizione di gioco nel database (upsert per chiave primaria).
     *
     * @param gameDefinitionLocal la definizione di gioco da salvare
     * @return la definizione di gioco persistita
     */
    @Override
    @Transactional
    public GameDefinitionLocal save(GameDefinitionLocal gameDefinitionLocal) {
        GameDefinitionLocalJpaEntity entity = mapper.toEntity(gameDefinitionLocal);
        GameDefinitionLocalJpaEntity savedEntity = jpaRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    /**
     * Recupera una definizione di gioco tramite il tipo di gioco.
     *
     * @param gameType il tipo di gioco da cercare
     * @return un {@code Optional} contenente la definizione di gioco, vuoto se non trovata o se il tipo è {@code null}
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<GameDefinitionLocal> findByGameType(GameType gameType) {
        if (gameType == null) {
            return Optional.empty();
        }
        return jpaRepository.findByGameType(gameType.name()).map(mapper::toDomain);
    }

    /**
     * Recupera tutte le definizioni di gioco ordinate per tipo in ordine crescente.
     *
     * @return una lista di tutte le definizioni di gioco
     */
    @Override
    @Transactional(readOnly = true)
    public List<GameDefinitionLocal> findAll() {
        return jpaRepository.findAllByOrderByGameTypeAsc().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * Verifica se esiste una definizione di gioco per il tipo specificato.
     *
     * @param gameType il tipo di gioco da verificare
     * @return {@code true} se la definizione esiste, {@code false} altrimenti o se il tipo è {@code null}
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsByGameType(GameType gameType) {
        if (gameType == null) {
            return false;
        }
        return jpaRepository.existsByGameType(gameType.name());
    }

    /**
     * Elimina una definizione di gioco per il tipo specificato.
     *
     * @param gameType il tipo di gioco da eliminare; se {@code null} l'operazione non viene eseguita
     */
    @Override
    @Transactional
    public void deleteByGameType(GameType gameType) {
        if (gameType == null) {
            return;
        }
        jpaRepository.deleteByGameType(gameType.name());
    }
}