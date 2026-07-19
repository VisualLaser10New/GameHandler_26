package com.gameplatform.shared.dto;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.WinCondition;

import java.time.Instant;
import java.util.List;

/**
 * Proiezione in sola lettura di una singola sessione di gioco completata,
 * restituita dall'endpoint {@code GET /api/players/me/matches/history} per il
 * giocatore locale. Esclude il dettaglio {@code GameResult} e la lista grezza
 * dei {@code participants} di {@link GameSessionDto}; l'endpoint locale applica
 * un filtro {@code status == COMPLETED} sui risultati di
 * {@code GameSessionRepository.findByParticipant} e proietta ciascuna sessione
 * in questa vista.
 *
 * @param sessionId         identificativo della sessione di gioco; non è nullo
 * @param gameType          tipologia di gioco giocata; non è nulla
 * @param startedAt         istante di avvio della sessione; non è nullo
 * @param endedAt           istante di conclusione della sessione; è nullo solo
 *                         se la sessione viene restituita senza una fine
 * @param durationSeconds   durata effettiva di gioco espressa in secondi; è
 *                         maggiore o uguale a 0
 * @param winnerId          identificativo dell'utente vincitore; è nullo per le
 *                         sessioni a squadre in cui il vincitore è un
 *                         {@code TeamId}
 * @param winCondition      condizione di vittoria applicata; non è nulla
 * @param participants      lista degli identificativi degli utenti partecipanti;
 *                         non è nulla e può essere vuota
 *
 * @see GameSessionDto
 */
public record PlayerMatchDto(
        String sessionId,
        GameType gameType,
        Instant startedAt,
        Instant endedAt,
        Integer durationSeconds,
        String winnerId,
        WinCondition winCondition,
        List<String> participants
) {
}
