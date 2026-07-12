package com.gameplatform.central.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gameplatform.central.domain.exception.InvalidGameDefinitionException;
import com.gameplatform.central.domain.model.GameDefinition;
import com.gameplatform.central.domain.model.OutboxEvent;
import com.gameplatform.central.domain.model.OutboxEventStatus;
import com.gameplatform.central.domain.ports.out.GameDefinitionRepository;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.GameDefinitionEventDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GameDefinitionService}, covering the upsert
 * create/update contract (preserving {@code createdAt} on update), outbox
 * emission of {@code GAME_DEFINITION_UPSERTED} (with the shared eventId UUID
 * contract between the outbox event id and {@link GameDefinitionEventDto#eventId()}),
 * the null-input guard, domain invariants propagation and the query use cases.
 * Pure Mockito (no Spring context).
 */
@ExtendWith(MockitoExtension.class)
class GameDefinitionServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-12T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @Mock
    private GameDefinitionRepository gameDefinitionRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private GameDefinitionService service;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        service = new GameDefinitionService(
                gameDefinitionRepository, outboxEventRepository, objectMapper, FIXED_CLOCK);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // upsert()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void upsert_createsNewDefinition_andWritesOutboxEvent() throws Exception {
        GameDefinition input = new GameDefinition(GameType.CHESS, "Scacchi", 2, 2, false, null, FIXED_NOW, FIXED_NOW);
        when(gameDefinitionRepository.existsByGameType(GameType.CHESS)).thenReturn(false);
        when(gameDefinitionRepository.save(any(GameDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

        service.upsert(input);

        ArgumentCaptor<GameDefinition> defCaptor = ArgumentCaptor.forClass(GameDefinition.class);
        verify(gameDefinitionRepository).save(defCaptor.capture());
        GameDefinition saved = defCaptor.getValue();
        assertThat(saved.getGameType()).isEqualTo(GameType.CHESS);
        assertThat(saved.getName()).isEqualTo("Scacchi");
        assertThat(saved.getMinPlayers()).isEqualTo(2);
        assertThat(saved.getMaxPlayers()).isEqualTo(2);
        assertThat(saved.isTeamAllowed()).isFalse();
        assertThat(saved.getRegistrationRules()).isNull();
        assertThat(saved.getCreatedAt()).isEqualTo(FIXED_NOW);
        assertThat(saved.getUpdatedAt()).isEqualTo(FIXED_NOW);

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());
        OutboxEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("GAME_DEFINITION_UPSERTED");
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getSentAt()).isNull();
        assertThat(event.getCreatedAt()).isEqualTo(FIXED_NOW);

        GameDefinitionEventDto dto = objectMapper.readValue(event.getPayload(), GameDefinitionEventDto.class);
        assertThat(dto.eventId()).isEqualTo(event.getId());
        assertThat(dto.eventType()).isEqualTo("GAME_DEFINITION_UPSERTED");
        assertThat(dto.gameType()).isEqualTo(GameType.CHESS);
        assertThat(dto.name()).isEqualTo("Scacchi");
        assertThat(dto.minPlayers()).isEqualTo(2);
        assertThat(dto.maxPlayers()).isEqualTo(2);
        assertThat(dto.teamAllowed()).isFalse();
        assertThat(dto.updatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void upsert_updatesExistingDefinition_preservesCreatedAt_andWritesOutboxEvent() {
        Instant earlier = Instant.parse("2026-01-01T00:00:00Z");
        GameDefinition existing = new GameDefinition(GameType.CHESS, "Scacchi", 2, 2, false, null, earlier, earlier);
        GameDefinition input = new GameDefinition(GameType.CHESS, "Scacchi (v2)", 2, 4, false, null, FIXED_NOW, FIXED_NOW);
        when(gameDefinitionRepository.existsByGameType(GameType.CHESS)).thenReturn(true);
        when(gameDefinitionRepository.findByGameType(GameType.CHESS)).thenReturn(Optional.of(existing));
        when(gameDefinitionRepository.save(any(GameDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

        GameDefinition result = service.upsert(input);

        ArgumentCaptor<GameDefinition> defCaptor = ArgumentCaptor.forClass(GameDefinition.class);
        verify(gameDefinitionRepository).save(defCaptor.capture());
        GameDefinition saved = defCaptor.getValue();
        assertThat(saved.getCreatedAt()).isEqualTo(earlier);
        assertThat(saved.getUpdatedAt()).isEqualTo(FIXED_NOW);
        assertThat(saved.getName()).isEqualTo("Scacchi (v2)");
        assertThat(saved.getMaxPlayers()).isEqualTo(4);
        assertThat(result).isSameAs(saved);

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("GAME_DEFINITION_UPSERTED");
    }

    @Test
    void upsert_throwsInvalidGameDefinitionException_whenInputIsNull() {
        assertThatThrownBy(() -> service.upsert(null))
                .isInstanceOf(InvalidGameDefinitionException.class);

        verifyNoInteractions(gameDefinitionRepository, outboxEventRepository);
    }

    @Test
    void upsert_propagatesDomainConstructorInvariants() {
        assertThatThrownBy(() -> new GameDefinition(GameType.CHESS, "x", 5, 3, false, null, FIXED_NOW, FIXED_NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minPlayers must be <= maxPlayers");

        verifyNoInteractions(gameDefinitionRepository, outboxEventRepository);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // findByGameType()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void findByGameType_returnsEmptyOptional_whenAbsent() {
        when(gameDefinitionRepository.findByGameType(GameType.CHESS)).thenReturn(Optional.empty());

        Optional<GameDefinition> result = service.findByGameType(GameType.CHESS);

        assertThat(result).isEmpty();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // findAll()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void findAll_returnsAllDefinitionsOrdered() {
        GameDefinition defChess = new GameDefinition(GameType.CHESS, "Scacchi", 2, 2, false, null, FIXED_NOW, FIXED_NOW);
        GameDefinition defFoosball = new GameDefinition(GameType.FOOSBALL, "Calcio Balilla", 2, 4, false, null, FIXED_NOW, FIXED_NOW);
        List<GameDefinition> all = List.of(defChess, defFoosball);
        when(gameDefinitionRepository.findAll()).thenReturn(all);

        List<GameDefinition> result = service.findAll();

        assertThat(result).isSameAs(all);
        assertThat(result).containsExactly(defChess, defFoosball);
    }
}