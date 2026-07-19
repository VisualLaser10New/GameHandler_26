package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.central.domain.model.User;
import com.gameplatform.central.domain.ports.out.UserRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.UserJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.mapper.UserMapper;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.UserJpaRepository;
import com.gameplatform.shared.domain.model.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adapter JPA che implementa il port {@link UserRepository} per la persistenza
 * degli utenti su MySQL. Fornisce il salvataggio e la ricerca per identificativo,
 * nome utente ed email.
 *
 * @see UserRepository
 */
@Component
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;
    private final UserMapper mapper;

    /**
     * Costruisce l'adapter iniettando il repository JPA e il mapper degli utenti.
     *
     * @param jpaRepository repository JPA per la gestione delle entit&agrave; utente
     * @param mapper        mapper che converte tra il modello di dominio e l'entit&agrave; JPA
     */
    public UserRepositoryAdapter(UserJpaRepository jpaRepository, UserMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    /**
     * Salva (o aggiorna) un utente e restituisce l'entit&agrave; persistita.
     *
     * @param user l'utente da persistere; non deve essere {@code null}
     * @return l'utente salvato, con eventuali valorizzazioni gestite dal database
     * @see UserJpaRepository#save
     */
    @Override
    public User save(User user) {
        UserJpaEntity entity = mapper.toEntity(user);
        UserJpaEntity savedEntity = jpaRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    /**
     * Restituisce l'utente identificato dal relativo identificativo.
     *
     * @param id l'identificativo dell'utente; se {@code null} restituisce {@link Optional#empty()}
     * @return l'{@link Optional} contenente l'utente trovato, o vuoto se assente o se {@code id} &egrave; {@code null}
     * @see UserJpaRepository#findById
     */
    @Override
    public Optional<User> findById(UserId id) {
        if (id == null) {
            return Optional.empty();
        }
        return jpaRepository.findById(id.value())
                .map(mapper::toDomain);
    }

    /**
     * Restituisce l'utente individuato dal nome utente.
     *
     * @param username il nome utente da cercare; se {@code null} restituisce {@link Optional#empty()}
     * @return l'{@link Optional} contenente l'utente trovato, o vuoto se assente o se {@code username} &egrave; {@code null}
     * @see UserJpaRepository#findByUsername
     */
    @Override
    public Optional<User> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return jpaRepository.findByUsername(username)
                .map(mapper::toDomain);
    }

    /**
     * Restituisce l'utente individuato dall'indirizzo email.
     *
     * @param email l'indirizzo email da cercare; se {@code null} restituisce {@link Optional#empty()}
     * @return l'{@link Optional} contenente l'utente trovato, o vuoto se assente o se {@code email} &egrave; {@code null}
     * @see UserJpaRepository#findByEmail
     */
    @Override
    public Optional<User> findByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        return jpaRepository.findByEmail(email)
                .map(mapper::toDomain);
    }

    /**
     * Restituisce l'elenco di tutti gli utenti persistiti.
     *
     * @return la lista di tutti gli utenti; lista vuota se non ve ne sono
     * @see UserJpaRepository#findAll
     */
    @Override
    public List<User> findAll() {
        return jpaRepository.findAll().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }
}
