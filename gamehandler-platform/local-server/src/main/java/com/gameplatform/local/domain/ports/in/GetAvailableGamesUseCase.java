package com.gameplatform.local.domain.ports.in;

import com.gameplatform.local.domain.model.Game;
import java.util.List;

/**
 * Use case per la consultazione del catalogo giochi disponibili.
 * Fornisce due modalit&agrave; di lettura: solo i giochi attualmente
 * disponibili oppure l'intero catalogo dei giochi registrati.
 *
 * @see com.gameplatform.local.domain.model.Game
 */
public interface GetAvailableGamesUseCase {
    /**
     * Restituisce l'elenco dei giochi attualmente disponibili.
     *
     * @return lista dei giochi disponibili
     */
    List<Game> getAvailable();

    /**
     * Restituisce l'elenco completo di tutti i giochi registrati.
     *
     * @return lista di tutti i giochi
     */
    List<Game> getAll();
}
