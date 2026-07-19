package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.UserJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.UserMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.LocalUserJpaRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.UserJpaRepository;
import com.gameplatform.shared.domain.model.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adapter JPA per il port {@link UserRepository}.
 * Gestisce la persistenza degli utenti, supportando sia la tabella
 * degli utenti replicati dal server centrale sia quella degli utenti
 * registrati localmente. La logica di salvataggio preserva il campo
 * {@code @Version} di Hibernate per garantire il corretto funzionamento
 * dell'ottimistic locking durante l'aggiornamento degli utenti esistenti.
 *
 * @see UserRepository
 * @see UserJpaRepository
 * @see LocalUserJpaRepository
 */
@Component
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;
    private final LocalUserJpaRepository localUserJpaRepository;
    private final UserMapper mapper;

    /**
     * Costruisce un nuovo adapter con le dipendenze necessarie.
     *
     * @param jpaRepository         repository JPA per gli utenti replicati
     * @param localUserJpaRepository repository JPA per gli utenti locali
     * @param mapper                mapper per la conversione tra entity e dominio
     */
    public UserRepositoryAdapter(UserJpaRepository jpaRepository, LocalUserJpaRepository localUserJpaRepository, UserMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.localUserJpaRepository = localUserJpaRepository;
        this.mapper = mapper;
    }

    /**
     * Salva un utente nel database, aggiornando l'entità gestita esistente
     * per preservare il campo {@code @Version} di Hibernate.
     *
     * @param user l'utente da salvare
     * @return l'utente persistito
     */
    @Override
    public User save(User user) {
        UserJpaEntity incoming = mapper.toEntity(user);
        Optional<UserJpaEntity> existing = jpaRepository.findById(incoming.getUserId());
        if (existing.isPresent()) {
            // Update the managed entity in-place so @Version is preserved.
            UserJpaEntity managed = existing.get();
            managed.setUsername(incoming.getUsername());
            managed.setPasswordHash(incoming.getPasswordHash());
            managed.setEmail(incoming.getEmail());
            managed.setRoles(incoming.getRoles());
            managed.setSyncedAt(incoming.getSyncedAt());
            managed.setEventTime(incoming.getEventTime());
            managed.setUpdatedAt(incoming.getUpdatedAt());
            UserJpaEntity saved = jpaRepository.save(managed);
            return mapper.toDomain(saved);
        }
        UserJpaEntity saved = jpaRepository.save(incoming);
        return mapper.toDomain(saved);
    }

    /**
     * Recupera un utente tramite il suo identificativo.
     *
     * @param userId l'identificativo dell'utente
     * @return un {@code Optional} contenente l'utente, vuoto se non trovato o se l'identificativo è {@code null}
     */
    @Override
    public Optional<User> findById(UserId userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return jpaRepository.findById(userId.value()).map(mapper::toDomain);
    }

    /**
     * Recupera un utente tramite username, cercando prima nella tabella
     * degli utenti replicati e, se non trovato, in quella degli utenti locali.
     *
     * @param username lo username dell'utente
     * @return un {@code Optional} contenente l'utente, vuoto se non trovato
     */
    @Override
    public Optional<User> findByUsername(String username) {
        Optional<User> replicated = jpaRepository.findByUsername(username).map(mapper::toDomain);
        if (replicated.isPresent()) {
            return replicated;
        }
        return localUserJpaRepository.findByUsername(username).map(mapper::toDomainFromLocalUser);
    }

    /**
     * Salva una lista di utenti in modalità batch, copiando il campo
     * {@code @Version} dalle righe esistenti per evitare eccezioni
     * {@code NonUniqueObjectException} di Hibernate durante l'aggiornamento.
     *
     * @param users la lista di utenti da salvare; se nulla o vuota l'operazione non viene eseguita
     */
    @Override
    public void saveAll(List<User> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        List<UserJpaEntity> entities = users.stream()
            .map(mapper::toEntity)
            .collect(Collectors.toList());
        // For existing rows, copy the @Version from the persisted row onto the
        // incoming detached entity so Hibernate's isNew() check returns false
        // and saveAll() performs an UPDATE (merge) rather than an INSERT —
        // which on a row that already exists raises NonUniqueObjectException.
        // New rows keep version=null so Hibernate assigns the initial 0 on insert.
        for (UserJpaEntity incoming : entities) {
            jpaRepository.findById(incoming.getUserId()).ifPresent(existing -> {
                incoming.setVersion(existing.getVersion());
            });
        }
        jpaRepository.saveAll(entities);
    }

    /**
     * Recupera tutti gli utenti dalla tabella degli utenti replicati.
     * Gli utenti registrati localmente (local_users) sono esclusi
     * intenzionalmente in quanto non soggetti a riconciliazione centrale.
     *
     * @return una lista di tutti gli utenti replicati
     */
    @Override
    public List<User> findAllReplicated() {
        // PIANO §7.B (deviation D1): the directory endpoint reads ONLY the
        // replicated_users table — local-signup users (local_users) are
        // intentionally excluded (they are not subject to central
        // reconciliation). The localUserJpaRepository is left untouched.
        return jpaRepository.findAll().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * Restituisce il numero totale di utenti nella tabella degli utenti replicati.
     * La tabella degli utenti locali è intenzionalmente esclusa dal conteggio.
     *
     * @return il conteggio degli utenti replicati
     */
    @Override
    public long count() {
        // M4 — backed by JpaRepository#count() (inherited), which counts the
        // replicated_users rows. The local_users table is intentionally
        // excluded: it is a separate identity store for users registered
        // directly on the local server (not subject to central reconciliation).
        return jpaRepository.count();
    }
}
