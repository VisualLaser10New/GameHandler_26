package com.gameplatform.local.domain.ports.in;

/**
 * Use case per la registrazione del server locale presso il sistema centrale.
 * L'implementazione gestisce i tentativi e i backoff fino a quando il sistema
 * centrale non &egrave; raggiungibile e conferma la registrazione.
 */
public interface RegisterLocalServerUseCase {
    /**
     * Tenta la registrazione del server locale presso il sistema centrale.
     *
     * @return {@code true} se la registrazione &egrave; avvenuta con successo, {@code false} altrimenti
     */
    boolean register();
}
