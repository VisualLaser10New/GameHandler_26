package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.shared.dto.TournamentSummaryEventDto;

import java.util.List;

/**
 * Porta di uscita per l'invio di un batch di eventi
 * {@code TOURNAMENT_SUMMARY_UPSERTED} all'endpoint
 * {@code PUT /internal/tournaments/summaries/sync} di un singolo server locale.
 * Gemello strutturale di {@link PushTournamentMatchToLocalServersPort} e
 * {@link PushGameDefinitionToLocalServersPort}.
 *
 * <p>Non è previsto alcun contratto di ack o di isolamento dei messaggi
 * avvelenati: l'upsert locale è idempotente per chiave primaria
 * {@code (tournamentId)}, pertanto un fallimento transitorio di trasporto viene
 * semplicemente ritentato tramite l'outbox al ciclo successivo dello
 * scheduler. Una tombstone con {@code deleted=true} è gestita lato locale come
 * una cancellazione per identificativo (rimozione della proiezione).</p>
 *
 * @throws com.gameplatform.central.domain.exception.TransientPushException
 *         in caso di fallimento transitorio di trasporto (il chiamante ritenta tramite l'outbox)
 * @see PushTournamentMatchToLocalServersPort
 * @see PushGameDefinitionToLocalServersPort
 * @see TournamentSummaryEventDto
 */
public interface PushTournamentSummaryToLocalServersPort {

    /**
     * Invia un batch di eventi di riepilogo di torneo al server locale indicato.
     *
     * @param events il batch di DTO dei riepiloghi da inviare; non deve essere {@code null}
     * @param server il singolo server locale attivo di destinazione; non deve essere {@code null}
     * @throws IllegalArgumentException in caso di parametri {@code null}
     * @throws com.gameplatform.central.domain.exception.TransientPushException
     *         in caso di fallimento transitorio di trasporto
     */
    void push(List<TournamentSummaryEventDto> events, RegisteredLocalServer server);
}
