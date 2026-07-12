package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gameplatform.central.domain.model.OutboxEvent;
import com.gameplatform.central.domain.model.OutboxEventStatus;
import com.gameplatform.central.domain.model.Tournament;
import com.gameplatform.central.domain.model.TournamentMatch;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentFormat;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import com.gameplatform.shared.domain.model.TournamentMatchStatus;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.TournamentMatchScheduledDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Pure-Mockito unit tests for {@link TournamentMatchOutboxAdapter}, covering
 * FASE 5 outbox emission for a newly-scheduled tournament match: a single
 * {@code OutboxEvent} row of type {@code TOURNAMENT_MATCH_SCHEDULED} with
 * {@code PENDING} status, a {@code createdAt} taken from the fixed clock, a
 * nullable {@code sentAt}, and a JSON payload whose {@code eventId} is the SAME
 * UUID shared with the {@link OutboxEvent#getId()} (mirrors
 * {@code LocalAdminBuildingService.writeOutboxEvent}).
 *
 * <p>Uses a real {@link ObjectMapper} with {@link JavaTimeModule} registered so
 * the nullable {@code Instant scheduledAt} field of
 * {@link TournamentMatchScheduledDto} round-trips through JSON correctly.
 */
@ExtendWith(MockitoExtension.class)
class TournamentMatchOutboxAdapterTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-12T10:00:00Z");
    private final Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private TournamentMatchOutboxAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TournamentMatchOutboxAdapter(outboxEventRepository, objectMapper, clock);
    }

    @Test
    void publishScheduled_savesPendingOutboxEventWithSharedUuidAndPayload() throws Exception {
        TournamentMatch match = scheduledMatch();
        Tournament tournament = tournament();
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);

        adapter.publishScheduled(match, tournament);

        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo("TOURNAMENT_MATCH_SCHEDULED");
        assertThat(saved.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(saved.getCreatedAt()).isEqualTo(FIXED_NOW);
        assertThat(saved.getSentAt()).isNull();

        // The OutboxEvent.id MUST equal the eventId parsed from the payload (shared UUID).
        TournamentMatchScheduledDto parsed = objectMapper.readValue(
                saved.getPayload(), TournamentMatchScheduledDto.class);
        assertThat(saved.getId()).isEqualTo(parsed.eventId());
        assertThat(parsed.eventId()).isNotBlank();
    }

    @Test
    void publishScheduled_payloadJsonContainsAllDtoFields() throws Exception {
        TournamentMatch match = scheduledMatch();
        Tournament tournament = tournament();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        adapter.publishScheduled(match, tournament);
        verify(outboxEventRepository).save(captor.capture());

        TournamentMatchScheduledDto parsed = objectMapper.readValue(
                captor.getValue().getPayload(), TournamentMatchScheduledDto.class);
        assertThat(parsed.eventType()).isEqualTo("TOURNAMENT_MATCH_SCHEDULED");
        assertThat(parsed.matchId()).isEqualTo("m-1");
        assertThat(parsed.tournamentId()).isEqualTo("t-1");
        assertThat(parsed.round()).isEqualTo(1);
        assertThat(parsed.bracketPosition()).isEqualTo(3);
        assertThat(parsed.participantA()).isEqualTo("PA");
        assertThat(parsed.participantB()).isEqualTo("PB");
        assertThat(parsed.gameType()).isEqualTo(GameType.CHESS);
        assertThat(parsed.gameId()).isNull();
        assertThat(parsed.status()).isEqualTo("SCHEDULED");
        assertThat(parsed.scheduledAt()).isNull();
    }

    private TournamentMatch scheduledMatch() {
        return new TournamentMatch(
                new TournamentMatchId("m-1"), new TournamentId("t-1"), 1, 3,
                "PA", "PB", null, null, null, null,
                TournamentMatchStatus.SCHEDULED, null, null, null);
    }

    private Tournament tournament() {
        return new Tournament(
                new TournamentId("t-1"), "Test Cup", GameType.CHESS, false, 1,
                TournamentFormat.SINGLE_ELIMINATION, TournamentStatus.IN_PROGRESS,
                FIXED_NOW, null, new UserId("admin"), FIXED_NOW);
    }
}