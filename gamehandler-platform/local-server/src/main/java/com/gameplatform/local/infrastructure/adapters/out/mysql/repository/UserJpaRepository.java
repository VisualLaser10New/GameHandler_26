package com.gameplatform.local.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.UserJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * Interfaccia Spring Data JPA per l'entità {@link UserJpaEntity}.
 * Fornisce operazioni di accesso ai dati per gli utenti della piattaforma,
 * inclusa la ricerca per nome utente.
 *
 * @see UserJpaEntity
 */
@Repository
public interface UserJpaRepository extends JpaRepository<UserJpaEntity, String> {
    /**
     * Recupera un utente in base al nome utente.
     *
     * @param username il nome utente da cercare
     * @return un {@link Optional} contenente l'entità utente, oppure vuoto se non trovata
     */
    Optional<UserJpaEntity> findByUsername(String username);
}
