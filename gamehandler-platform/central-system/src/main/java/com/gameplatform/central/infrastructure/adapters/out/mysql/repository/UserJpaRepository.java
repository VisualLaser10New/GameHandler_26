package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.UserJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository JPA per l'accesso ai dati degli utenti della piattaforma.
 * <p>
 * Fornisce metodi di interrogazione per la ricerca di utenti tramite nome
 * utente o indirizzo email, utilizzati principalmente durante il processo
 * di autenticazione e registrazione.
 * </p>
 *
 * @see UserJpaEntity
 */
@Repository
public interface UserJpaRepository extends JpaRepository<UserJpaEntity, String> {

    /**
     * Restituisce l'utente associato al nome utente specificato, se presente.
     *
     * @param username il nome utente da cercare (non null)
     * @return un {@code Optional} contenente l'utente se trovato, vuoto altrimenti
     */
    Optional<UserJpaEntity> findByUsername(String username);

    /**
     * Restituisce l'utente associato all'indirizzo email specificato, se presente.
     *
     * @param email l'indirizzo email da cercare (non null)
     * @return un {@code Optional} contenente l'utente se trovato, vuoto altrimenti
     */
    Optional<UserJpaEntity> findByEmail(String email);
}
