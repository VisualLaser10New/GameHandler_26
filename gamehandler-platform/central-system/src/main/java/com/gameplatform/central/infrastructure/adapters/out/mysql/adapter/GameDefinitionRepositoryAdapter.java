package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.central.domain.model.GameDefinition;
import com.gameplatform.central.domain.ports.out.GameDefinitionRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.GameDefinitionJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.mapper.GameDefinitionMapper;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.GameDefinitionJpaRepository;
import com.gameplatform.shared.domain.model.GameType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * JPA adapter for the {@link GameDefinitionRepository} port. Matches the
 * {@code LocalAdminBuildingRepositoryAdapter} shape: constructor-injects the
 * JPA repository + mapper; {@code save} is an upsert by the business PK
 * {@code game_type} (the underlying {@link GameDefinitionJpaRepository#save}
 * merges an existing row if the game_type PK is already present).
 */
@Component
public class GameDefinitionRepositoryAdapter implements GameDefinitionRepository {

    private final GameDefinitionJpaRepository jpaRepository;
    private final GameDefinitionMapper mapper;

    /**
     * Costruisce l'adapter iniettando il repository JPA e il mapper delle definizioni di gioco.
     *
     * @param jpaRepository repository JPA per la gestione delle entit&agrave; di definizione gioco
     * @param mapper        mapper che converte tra il modello di dominio e l'entit&agrave; JPA
     */
    public GameDefinitionRepositoryAdapter(GameDefinitionJpaRepository jpaRepository,
                                           GameDefinitionMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    /**
     * Salva (o aggiorna) una definizione di gioco e restituisce l'entit&agrave; persistita.
     *
     * @param gameDefinition la definizione di gioco da persistere; non deve essere {@code null}
     * @return la definizione di gioco salvata, con eventuali valorizzazioni gestite dal database
     * @see GameDefinitionJpaRepository#save
     */
    @Override
    @Transactional
    public GameDefinition save(GameDefinition gameDefinition) {
        GameDefinitionJpaEntity entity = mapper.toEntity(gameDefinition);
        GameDefinitionJpaEntity savedEntity = jpaRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    /**
     * Restituisce la definizione di gioco associata al tipo di gioco indicato.
     *
     * @param gameType il tipo di gioco di cui cercare la definizione; se {@code null} restituisce {@link Optional#empty()}
     * @return l'{@link Optional} contenente la definizione trovata, o vuoto se assente o se {@code gameType} &egrave; {@code null}
     * @see GameDefinitionJpaRepository#findByGameType
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<GameDefinition> findByGameType(GameType gameType) {
        if (gameType == null) {
            return Optional.empty();
        }
        return jpaRepository.findByGameType(gameType.name()).map(mapper::toDomain);
    }

    /**
     * Restituisce l'elenco di tutte le definizioni di gioco ordinate per tipo.
     *
     * @return la lista delle definizioni di gioco; lista vuota se non ve ne sono
     * @see GameDefinitionJpaRepository#findAllByOrderByGameTypeAsc
     */
    @Override
    @Transactional(readOnly = true)
    public List<GameDefinition> findAll() {
        return jpaRepository.findAllByOrderByGameTypeAsc().stream()
                .map(mapper::toDomain)
                .toList();
    }

    /**
     * Verifica l'esistenza di una definizione di gioco per il tipo indicato.
     *
     * @param gameType il tipo di gioco da verificare; se {@code null} restituisce {@code false}
     * @return {@code true} se esiste una definizione per {@code gameType}, {@code false} altrimenti
     * @see GameDefinitionJpaRepository#existsByGameType
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
     * Elimina la definizione di gioco associata al tipo indicato.
     *
     * @param gameType il tipo di gioco di cui eliminare la definizione; se {@code null} il metodo non effettua alcuna operazione
     * @see GameDefinitionJpaRepository#deleteByGameType
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
