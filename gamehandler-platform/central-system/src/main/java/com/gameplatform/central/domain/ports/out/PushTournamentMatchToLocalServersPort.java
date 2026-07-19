package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.shared.dto.TournamentMatchScheduledDto;

import java.util.List;

/**
 * Porta di uscita per l'invio di un batch di eventi
 * {@code TOURNAMENT_MATCH_SCHEDULED} all'endpoint
 * {@code PUT /internal/tournaments/matches/sync} di un singolo server locale.
 * Gemello strutturale di {@link PushGameDefinitionToLocalServersPort}.
 *
 * <p>Non è previsto alcun contratto di ack o di isolamento dei messaggi
 * avvelenati: l'upsert locale è idempotente per chiave primaria
 * {@code (matchId)}, pertanto un fallimento transitorio di trasporto viene
 * semplicemente ritentato tramite l'outbox al ciclo successivo dello
 * scheduler.</p>
 *
 * @throws com.gameplatform.central.domain.exception.TransientPushException
 *         in caso di fallimento transitorio di trasporto (il chiamante ritenta tramite l'outbox)
 * @see PushGameDefinitionToLocalServersPort
 * @see TournamentMatchScheduledDto
 */
public interface PushTournamentMatchToLocalServersPort {

    /**
     * Invia un batch di eventi di partite di torneo programmate al server locale indicato.
     *
     * @param events il batch di DTO delle partite programmate da inviare, uno per ogni partita interessata
     *               instradata verso l'edificio; non deve essere {@code null}
     * @param server il singolo server locale attivo di destinazione; non deve essere {@code null}
     * @throws IllegalArgumentException in caso di parametri {@code null}
     * @throws com.gameplatform.central.domain.exception.TransientPushException
     *         in caso di fallimento transitorio di trasporto
     */
    void pushTournamentMatch(List<TournamentMatchScheduledDto> events, RegisteredLocalServer server);
}
