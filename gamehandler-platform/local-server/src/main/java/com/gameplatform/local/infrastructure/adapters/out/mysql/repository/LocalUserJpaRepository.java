package com.gameplatform.local.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.LocalUserJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * Interfaccia Spring Data JPA per l'entità {@link LocalUserJpaEntity}.
 * Fornisce operazioni di accesso ai dati per gli utenti locali della
 * piattaforma, inclusa la ricerca per nome utente ed email, e la verifica
 * di esistenza degli stessi.
 *
 * @see LocalUserJpaEntity
 */
@Repository
public interface LocalUserJpaRepository extends JpaRepository<LocalUserJpaEntity, String> {
    /**
     * Recupera un utente locale in base al nome utente.
     *
     * @param username il nome utente da cercare
     * @return un {@link Optional} contenente l'entità utente, oppure vuoto se non trovata
     */
    Optional<LocalUserJpaEntity> findByUsername(String username);

    /**
     * Recupera un utente locale in base all'indirizzo email.
     *
     * @param email l'indirizzo email da cercare
     * @return un {@link Optional} contenente l'entità utente, oppure vuoto se non trovata
     */
    Optional<LocalUserJpaEntity> findByEmail(String email);

    /**
     * Verifica se esiste un utente locale con il nome utente specificato.
     *
     * @param username il nome utente da verificare
     * @return {@code true} se esiste un utente con il nome indicato, {@code false} altrimenti
     */
    boolean existsByUsername(String username);

    /**
     * Verifica se esiste un utente locale con l'indirizzo email specificato.
     *
     * @param email l'indirizzo email da verificare
     * @return {@code true} se esiste un utente con l'email indicata, {@code false} altrimenti
     */
    boolean existsByEmail(String email);
}
