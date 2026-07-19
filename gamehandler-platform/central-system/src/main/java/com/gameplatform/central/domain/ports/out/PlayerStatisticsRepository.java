package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.PlayerStatistics;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;

import java.util.List;
import java.util.Optional;

/**
 * Porta di persistenza per la tabella read-model {@code player_statistics}.
 *
 * <p>Contiene contatori aggregati per giocatore e per tipo di gioco, mantenuti
 * in sincronia con {@code player_match_facts} dalla proiezione
 * {@code SyncEventProcessor}. L'incremento è progettato per essere sicuro in
 * presenza di concorrenza tramite un lock pessimistico.</p>
 *
 * @see PlayerStatistics
 * @see #increment(UserId, GameType, boolean, java.time.Instant)
 */
public interface PlayerStatisticsRepository {

    /**
     * Restituisce tutte le righe di statistiche relative all'utente indicato.
     *
     * @param userId l'identificativo dell'utente; non deve essere {@code null}
     * @return la lista delle statistiche dell'utente; mai {@code null}, vuota se l'utente non ha giocato alcuna partita
     * @throws IllegalArgumentException se {@code userId} è {@code null}
     */
    List<PlayerStatistics> findByUserId(UserId userId);

    /**
     * Restituisce la riga di statistiche per la coppia utente e tipo di gioco indicata.
     *
     * @param userId   l'identificativo dell'utente; non deve essere {@code null}
     * @param gameType il tipo di gioco; non deve essere {@code null}
     * @return un {@link Optional} contenente la statistica trovata, o vuoto se non esiste
     * @throws IllegalArgumentException se {@code userId} o {@code gameType} sono {@code null}
     */
    Optional<PlayerStatistics> findByUserIdAndGameType(UserId userId, GameType gameType);

    /**
     * Registra atomicamente una partita completata aggiuntiva per la coppia utente
     * e tipo di gioco, incrementando i contatori e aggiornando l'istante di ultima
     * partita giocata.
     *
     * <p>L'operazione è sicura in presenza di concorrenza tramite un lock in
     * scrittura pessimistico e deve essere invocata all'interno di una transazione
     * attiva.</p>
     *
     * @param userId   l'identificativo dell'utente; non deve essere {@code null}
     * @param gameType il tipo di gioco; non deve essere {@code null}
     * @param won      {@code true} se la partita è stata vinta, {@code false} altrimenti
     * @param endedAt  l'istante di termine della partita; non deve essere {@code null}
     * @throws IllegalArgumentException se {@code userId}, {@code gameType} o {@code endedAt} sono {@code null}
     * @throws IllegalStateException    se non è attiva alcuna transazione
     */
    void increment(UserId userId, GameType gameType, boolean won, java.time.Instant endedAt);
}
