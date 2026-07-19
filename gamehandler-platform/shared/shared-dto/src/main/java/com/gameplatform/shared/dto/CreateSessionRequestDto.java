package com.gameplatform.shared.dto;

import com.gameplatform.shared.domain.model.GameType;
import java.util.List;

/**
 * DTO di richiesta utilizzato per creare una nuova sessione di gioco sulla piattaforma.
 * Trasporta i dati necessari ad avviare una partita, tra cui il gioco di riferimento,
 * il tipo di gioco, i partecipanti e gli eventuali riferimenti a prenotazione o incontro di torneo.
 *
 * @see com.gameplatform.shared.domain.model.GameType
 */
public record CreateSessionRequestDto(
    /**
     * Restituisce l'identificativo univoco del gioco per cui viene creata la sessione.
     *
     * @return l'identificativo del gioco; non deve essere {@code null} né vuoto
     */
    String gameId,

    /**
     * Restituisce il tipo di gioco che determina le regole e la modalità della sessione.
     *
     * @return il tipo di gioco associato; non deve essere {@code null}
     * @see com.gameplatform.shared.domain.model.GameType
     */
    GameType gameType,

    /**
     * Restituisce l'elenco degli identificativi dei partecipanti alla sessione.
     *
     * @return la lista dei partecipanti; non deve essere {@code null}, può essere vuota
     *         ma in tal caso la sessione non avrà giocatori associati
     */
    List<String> participants,

    /**
     * Restituisce l'identificativo della prenotazione a cui la sessione è collegata.
     *
     * @return l'identificativo della prenotazione, oppure {@code null} se la sessione
     *         non deriva da una prenotazione
     */
    String reservationId,

    /**
     * Restituisce l'identificativo dell'incontro di torneo a cui la sessione è associata.
     *
     * @return l'identificativo dell'incontro di torneo, oppure {@code null} se la sessione
     *         non fa parte di un torneo
     */
    String tournamentMatchId
) {}
