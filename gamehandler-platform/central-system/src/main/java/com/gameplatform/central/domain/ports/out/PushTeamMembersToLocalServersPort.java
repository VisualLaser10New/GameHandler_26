package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.shared.dto.TeamMembersEventDto;

import java.util.List;

/**
 * Porta di uscita per l'invio di un batch di eventi
 * {@code TEAM_MEMBERS_UPSERTED} all'endpoint
 * {@code PUT /internal/tournaments/teams/members/sync} di un singolo server
 * locale. Gemello strutturale di
 * {@link PushTournamentParticipantsToLocalServersPort}.
 *
 * <p>Non è previsto alcun contratto di ack o di isolamento dei messaggi
 * avvelenati: l'upsert locale è uno snapshot di cancellazione e inserimento per
 * {@code tournamentId}, pertanto un fallimento transitorio di trasporto viene
 * semplicemente ritentato tramite l'outbox al ciclo successivo dello
 * scheduler.</p>
 *
 * @throws com.gameplatform.central.domain.exception.TransientPushException
 *         in caso di fallimento transitorio di trasporto (il chiamante ritenta tramite l'outbox)
 * @see PushTournamentParticipantsToLocalServersPort
 * @see TeamMembersEventDto
 */
public interface PushTeamMembersToLocalServersPort {

    /**
     * Invia un batch di eventi di membri di squadra al server locale indicato.
     *
     * @param events il batch di DTO dei membri di squadra da inviare; non deve essere {@code null}
     * @param server il singolo server locale attivo di destinazione; non deve essere {@code null}
     * @throws IllegalArgumentException in caso di parametri {@code null}
     * @throws com.gameplatform.central.domain.exception.TransientPushException
     *         in caso di fallimento transitorio di trasporto
     */
    void push(List<TeamMembersEventDto> events, RegisteredLocalServer server);
}
