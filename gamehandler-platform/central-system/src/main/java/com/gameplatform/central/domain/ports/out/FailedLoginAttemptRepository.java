package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.FailedLoginAttempt;
import java.time.Instant;

/**
 * Porta di persistenza per i tentativi di accesso non riusciti di un utente.
 *
 * <p>Fornisce le operazioni necessarie a registrare un tentativo fallito e a
 * contare i tentativi effettuati in un intervallo temporale, a supporto delle
 * politiche di blocco e di sicurezza degli account.</p>
 *
 * @see FailedLoginAttempt
 */
public interface FailedLoginAttemptRepository {

    /**
     * Persiste un nuovo tentativo di accesso non riuscito.
     *
     * @param attempt il tentativo di accesso fallito da registrare; non deve essere {@code null}
     * @throws IllegalArgumentException se {@code attempt} è {@code null}
     */
    void save(FailedLoginAttempt attempt);

    /**
     * Conta i tentativi di accesso non riusciti per l'utente indicato a partire
     * dall'istante specificato.
     *
     * <p>Eventuali tentativi precedenti a {@code since} non vengono considerati.
     * Restituisce {@code 0} se l'utente non ha tentativi falliti nell'intervallo.</p>
     *
     * @param username il nome utente di cui contare i tentativi; non deve essere {@code null}
     * @param since     l'istante a partire dal quale contare i tentativi; non deve essere {@code null}
     * @return il numero di tentativi falliti, sempre non negativo
     * @throws IllegalArgumentException se {@code username} o {@code since} sono {@code null}
     */
    long countFailedAttempts(String username, Instant since);
}
