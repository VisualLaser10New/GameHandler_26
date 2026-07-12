package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.model.OutboxEvent;
import com.gameplatform.central.domain.model.OutboxEventStatus;
import com.gameplatform.central.domain.model.Tournament;
import com.gameplatform.central.domain.model.TournamentMatch;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.central.domain.ports.out.TournamentMatchOutboxPort;
import com.gameplatform.shared.dto.TournamentMatchScheduledDto;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Adapter implementing {@link TournamentMatchOutboxPort}. Lifts the canonical
 * outbox-write helper body of
 * {@code LocalAdminBuildingService.writeOutboxEvent} into a port implementation:
 * one shared {@link UUID} for both {@link OutboxEvent#getId()} and
 * {@link TournamentMatchScheduledDto#eventId()}, JSON-serialised payload, and a
 * single {@link OutboxEventRepository#save} within the caller's transaction.
 *
 * <p>The class is {@code @Component} (matching every other adapter in this
 * package) and deliberately carries NO class-level {@code @Transactional}: the
 * caller ({@code TournamentBracketService.schedule}) drives the atomicity
 * boundary. The {@code save} call participates in the caller's tx — exactly
 * like the inline outbox write inside {@code LocalAdminBuildingService.assignBuildings}.</p>
 *
 * <p>Java time module (Jackson {@code JavaTimeModule}) is already registered on
 * the central {@code ObjectMapper} bean (used by every service that serialises
 * outbox DTOs with {@code Instant} fields — see
 * {@code GameDefinitionService.writeOutboxEvent}); hence {@code scheduledAt} (nullable)
 * serialises correctly out of the box.</p>
 */
@Component
public class TournamentMatchOutboxAdapter implements TournamentMatchOutboxPort {

    private static final String EVENT_TYPE = "TOURNAMENT_MATCH_SCHEDULED";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TournamentMatchOutboxAdapter(OutboxEventRepository outboxEventRepository,
                                        ObjectMapper objectMapper,
                                        Clock clock) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void publishScheduled(TournamentMatch match, Tournament tournament) {
        String eventId = UUID.randomUUID().toString();
        TournamentMatchScheduledDto dto = new TournamentMatchScheduledDto(
                eventId,
                EVENT_TYPE,
                match.getMatchId().value(),
                tournament.getTournamentId().value(),
                match.getRound(),
                match.getBracketPosition(),
                match.getParticipantA(),
                match.getParticipantB(),   // nullable for BYE — byes never reach this method
                tournament.getGameType(),
                null,                      // gameId — assigned in FASE 6 when the match is pushed to a building
                match.getStatus().name(),
                match.getScheduledAt(),    // null in FASE 5 (assigned in FASE 6 push)
                null                       // buildingId — assigned in FASE 6 drain branch (round-robin)
        );

        String payload;
        try {
            payload = objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize tournament match scheduled event to JSON: ", e);
        }

        OutboxEvent event = new OutboxEvent(
                eventId, EVENT_TYPE, payload, OutboxEventStatus.PENDING, Instant.now(clock), null);
        outboxEventRepository.save(event);
    }
}
