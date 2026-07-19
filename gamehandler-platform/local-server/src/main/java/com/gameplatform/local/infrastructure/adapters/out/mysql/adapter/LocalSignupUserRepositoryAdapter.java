package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.model.LocalSignupUser;
import com.gameplatform.local.domain.ports.out.LocalSignupUserRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.LocalUserJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.LocalUserMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.LocalUserJpaRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.UserJpaRepository;
import org.springframework.stereotype.Component;

/**
 * Adapter JPA per il port {@link LocalSignupUserRepository}.
 * Gestisce la persistenza degli utenti registrati localmente,
 * verificando l'univocità di username ed email sia nella tabella
 * degli utenti locali che in quella degli utenti replicati.
 *
 * @see LocalSignupUserRepository
 * @see LocalUserJpaRepository
 * @see UserJpaRepository
 */
@Component
public class LocalSignupUserRepositoryAdapter implements LocalSignupUserRepository {

    private final LocalUserJpaRepository jpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final LocalUserMapper mapper;

    /**
     * Costruisce un nuovo adapter con le dipendenze necessarie.
     *
     * @param jpaRepository    repository JPA per gli utenti locali
     * @param userJpaRepository repository JPA per gli utenti replicati
     * @param mapper           mapper per la conversione tra entity e dominio
     */
    public LocalSignupUserRepositoryAdapter(LocalUserJpaRepository jpaRepository, UserJpaRepository userJpaRepository, LocalUserMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.userJpaRepository = userJpaRepository;
        this.mapper = mapper;
    }

    /**
     * Verifica se esiste un utente con lo username specificato,
     * controllando sia la tabella degli utenti locali che quella degli utenti replicati.
     *
     * @param username lo username da verificare
     * @return {@code true} se lo username è già utilizzato
     */
    @Override
    public boolean existsByUsername(String username) {
        return jpaRepository.existsByUsername(username) || userJpaRepository.findByUsername(username).isPresent();
    }

    /**
     * Verifica se esiste un utente locale con l'email specificata.
     *
     * @param email l'email da verificare
     * @return {@code true} se l'email è già utilizzata, {@code false} se l'email è nulla/vuota o non trovata
     */
    @Override
    public boolean existsByEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return jpaRepository.existsByEmail(email);
    }

    /**
     * Salva un utente registrato localmente nel database.
     *
     * @param user l'utente locale da salvare
     * @return l'utente locale persistito
     */
    @Override
    public LocalSignupUser save(LocalSignupUser user) {
        LocalUserJpaEntity entity = mapper.toEntity(user);
        LocalUserJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }
}
