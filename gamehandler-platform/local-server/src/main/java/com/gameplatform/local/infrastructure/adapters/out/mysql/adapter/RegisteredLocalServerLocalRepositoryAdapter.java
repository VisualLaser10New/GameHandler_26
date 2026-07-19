package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.model.RegisteredLocalServerLocal;
import com.gameplatform.local.domain.ports.out.RegisteredLocalServerLocalRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.RegisteredLocalServerLocalJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.RegisteredLocalServerLocalMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.RegisteredLocalServerLocalJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adapter JPA per il port {@link RegisteredLocalServerLocalRepository}.
 * Gestisce la persistenza dei server locali registrati, con operazioni
 * di upsert per chiave primaria {@code buildingId} e funzionalità di
 * attivazione/disattivazione dei server.
 *
 * @see RegisteredLocalServerLocalRepository
 * @see RegisteredLocalServerLocalJpaRepository
 */
@Component
public class RegisteredLocalServerLocalRepositoryAdapter implements RegisteredLocalServerLocalRepository {

    private final RegisteredLocalServerLocalJpaRepository jpaRepository;
    private final RegisteredLocalServerLocalMapper mapper;

    /**
     * Costruisce un nuovo adapter con le dipendenze necessarie.
     *
     * @param jpaRepository repository JPA per i server locali registrati
     * @param mapper        mapper per la conversione tra entity e dominio
     */
    public RegisteredLocalServerLocalRepositoryAdapter(RegisteredLocalServerLocalJpaRepository jpaRepository,
                                                        RegisteredLocalServerLocalMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    /**
     * Salva un server locale registrato nel database (upsert per chiave primaria).
     *
     * @param server il server locale da salvare
     * @return il server locale persistito, {@code null} se l'argomento è {@code null}
     */
    @Override
    @Transactional
    public RegisteredLocalServerLocal save(RegisteredLocalServerLocal server) {
        if (server == null) {
            return null;
        }
        RegisteredLocalServerLocalJpaEntity entity = mapper.toEntity(server);
        RegisteredLocalServerLocalJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    /**
     * Recupera un server locale registrato tramite l'identificativo dell'edificio.
     *
     * @param buildingId l'identificativo dell'edificio
     * @return un {@code Optional} contenente il server locale, vuoto se non trovato o se l'identificativo è nullo/vuoto
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<RegisteredLocalServerLocal> findById(String buildingId) {
        if (buildingId == null || buildingId.isBlank()) {
            return Optional.empty();
        }
        return jpaRepository.findById(buildingId).map(mapper::toDomain);
    }

    /**
     * Recupera tutti i server locali registrati.
     *
     * @return una lista completa di tutti i server locali registrati
     */
    @Override
    @Transactional(readOnly = true)
    public List<RegisteredLocalServerLocal> findAll() {
        return jpaRepository.findAll().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * Elimina un server locale registrato tramite l'identificativo dell'edificio.
     *
     * @param buildingId l'identificativo dell'edificio da eliminare; se nullo o vuoto l'operazione non viene eseguita
     */
    @Override
    @Transactional
    public void deleteById(String buildingId) {
        if (buildingId == null || buildingId.isBlank()) {
            return;
        }
        jpaRepository.deleteById(buildingId);
    }

    /**
     * Imposta lo stato di attivazione di un server locale registrato.
     *
     * @param buildingId l'identificativo dell'edificio
     * @param active     {@code true} per attivare, {@code false} per disattivare il server
     * @return un {@code Optional} contenente il server aggiornato, vuoto se non trovato o se l'identificativo è nullo/vuoto
     */
    @Override
    @Transactional
    public Optional<RegisteredLocalServerLocal> setActive(String buildingId, boolean active) {
        if (buildingId == null || buildingId.isBlank()) {
            return Optional.empty();
        }
        return jpaRepository.findById(buildingId).map(entity -> {
            entity.setActive(active);
            entity.setUpdatedAt(java.time.Instant.now());
            RegisteredLocalServerLocalJpaEntity saved = jpaRepository.save(entity);
            return mapper.toDomain(saved);
        });
    }
}