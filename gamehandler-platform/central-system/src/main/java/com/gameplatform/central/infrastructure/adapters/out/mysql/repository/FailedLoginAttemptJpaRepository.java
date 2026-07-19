package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.FailedLoginAttemptJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;

/**
 * Repository JPA per l'accesso ai dati dei tentativi di accesso falliti.
 * <p>
 * Utilizzato per implementare politiche di blocco temporaneo dell'account
 * in seguito a troppi tentativi falliti consecutivi. Il metodo di conteggio
 * consente di determinare quanti tentativi falliti sono stati effettuati
 * per un dato utente in una finestra temporale specifica.
 * </p>
 *
 * @see FailedLoginAttemptJpaEntity
 */
public interface FailedLoginAttemptJpaRepository extends JpaRepository<FailedLoginAttemptJpaEntity, String> {

    /**
     * Conta i tentativi di accesso falliti per l'utente specificato a partire
     * dall'istante indicato.
     *
     * @param username il nome utente di cui contare i tentativi falliti (non null)
     * @param since    l'istante temporale a partire dal quale considerare i tentativi (non null)
     * @return il numero di tentativi falliti per l'utente dopo l'istante specificato (zero o positivo)
     */
    long countByUsernameAndAttemptTimeAfter(String username, Instant since);
}
