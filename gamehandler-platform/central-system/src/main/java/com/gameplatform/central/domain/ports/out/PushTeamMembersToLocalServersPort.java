package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.shared.dto.TeamMembersEventDto;

import java.util.List;

/**
 * Outbound port for pushing a batch of {@code TEAM_MEMBERS_UPSERTED} events
 * to a single Local Server's
 * {@code PUT /internal/tournaments/teams/members/sync} endpoint. Structural
 * twin of {@link PushTournamentParticipantsToLocalServersPort}.
 *
 * <p>No ack / poison-isolation: the local upsert is a delete+insert snapshot
 * by {@code tournamentId}, so a transient transport failure just retries via
 * the outbox on the next scheduler tick.</p>
 *
 * @throws com.gameplatform.central.domain.exception.TransientPushException
 *         on transient transport failure (caller retries via the outbox)
 */
public interface PushTeamMembersToLocalServersPort {

    /**
     * Pushes a batch of team-members upsert events to a single local server.
     *
     * @param events the team-members DTO batch to push
     * @param server the single target active local server
     */
    void push(List<TeamMembersEventDto> events, RegisteredLocalServer server);
}