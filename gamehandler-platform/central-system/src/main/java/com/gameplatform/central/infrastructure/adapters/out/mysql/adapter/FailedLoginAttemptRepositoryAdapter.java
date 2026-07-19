package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.central.domain.model.FailedLoginAttempt;
import com.gameplatform.central.domain.ports.out.FailedLoginAttemptRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.FailedLoginAttemptJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.FailedLoginAttemptJpaRepository;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;

/**
 * Adapter JPA che implementa il port {@link FailedLoginAttemptRepository} per la
 * persistenza dei tentativi di accesso falliti su MySQL. Fornisce il salvataggio
 * di un tentativo e il conteggio dei tentativi recenti associati a un utente.
 *
 * @see FailedLoginAttemptRepository
 */
@Component
public class FailedLoginAttemptRepositoryAdapter implements FailedLoginAttemptRepository {

    private final FailedLoginAttemptJpaRepository jpaRepository;

    /**
     * Costruisce l'adapter iniettando il repository JPA dedicato ai tentativi di accesso falliti.
     *
     * @param jpaRepository repository JPA per la gestione delle entit&agrave; di tentativo fallito
     */
    public FailedLoginAttemptRepositoryAdapter(FailedLoginAttemptJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    /**
     * Persiste un nuovo tentativo di accesso fallito associandolo a un identificativo generato.
     *
     * @param attempt il tentativo di accesso fallito da salvare; se {@code null} il metodo non effettua alcuna operazione
     */
    @Override
    public void save(FailedLoginAttempt attempt) {
        if (attempt == null) {
            return;
        }
        String id = UUID.randomUUID().toString();
        FailedLoginAttemptJpaEntity entity = new FailedLoginAttemptJpaEntity(
                id,
                attempt.username(),
                attempt.attemptTime()
        );
        jpaRepository.save(entity);
    }

    /**
     * Restituisce il numero di tentativi di accesso falliti registrati per l'utente
     * a partire dall'istante temporale indicato.
     *
     * @param username il nome utente di cui contare i tentativi falliti; se {@code null} restituisce {@code 0}
     * @param since    l'istante a partire dal quale contare i tentativi; se {@code null} restituisce {@code 0}
     * @return il numero di tentativi falliti successivi a {@code since}; {@code 0} se non ve ne sono o se un argomento &egrave; {@code null}
     */
    @Override
    public long countFailedAttempts(String username, Instant since) {
        if (username == null || since == null) {
            return 0;
        }
        return jpaRepository.countByUsernameAndAttemptTimeAfter(username, since);
    }
}
