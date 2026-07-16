package com.gameplatform.central.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gameplatform.central.domain.exception.InvalidTournamentException;
import com.gameplatform.central.domain.exception.InvalidTournamentStateException;
import com.gameplatform.central.domain.exception.TournamentNotFoundException;
import com.gameplatform.central.domain.model.GameDefinition;
import com.gameplatform.central.domain.model.OutboxEvent;
import com.gameplatform.central.domain.model.OutboxEventStatus;
import com.gameplatform.central.domain.model.Tournament;
import com.gameplatform.central.domain.ports.out.GameDefinitionRepository;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.central.domain.ports.out.TournamentBuildingRepository;
import com.gameplatform.central.domain.ports.out.TournamentParticipantRepository;
import com.gameplatform.central.domain.ports.out.TournamentRepository;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentFormat;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.TournamentDto;
import com.gameplatform.shared.dto.TournamentSummaryEventDto;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TournamentService}, covering the FASE 4 tournament
 * CRUD + lifecycle use cases: {@code create} (forced {@code DRAFT}, building
 * linkage, team-policy validation against {@link GameDefinition}),
 * {@code open}/{@code cancel} state-machine transitions, and the query use
 * cases; plus the &sect;7.A.1 {@code update}/{@code delete} mutating use cases
 * (DRAFT-only guard via {@link Tournament#update} / an explicit status check,
 * building replacement, atomic {@code TOURNAMENT_SUMMARY_UPSERTED} outbox
 * emission with a {@code deleted=true} tombstone for deletes). Pure Mockito
 * (no Spring context); uses a real {@link Clock#fixed} so {@code createdAt}
 * is deterministic, and a real {@link ObjectMapper} (with {@link JavaTimeModule})
 * so the outbox payload can be round-tripped and asserted.
 */
@ExtendWith(MockitoExtension.class)
class TournamentServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-12T10:00:00Z");
    private final Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @Mock
    private TournamentRepository tournamentRepository;
    @Mock
    private TournamentBuildingRepository tournamentBuildingRepository;
    @Mock
    private TournamentParticipantRepository tournamentParticipantRepository;
    @Mock
    private GameDefinitionRepository gameDefinitionRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;

    private ObjectMapper objectMapper;
    private TournamentService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new TournamentService(
                tournamentRepository, tournamentBuildingRepository,
                tournamentParticipantRepository, gameDefinitionRepository, clock,
                outboxEventRepository, objectMapper);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // create()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void create_persistsDraftTournament_whenValidIndividualRequest() {
        Tournament input = new Tournament(
                new TournamentId("t-1"), "Test Cup", GameType.CHESS, false, 1,
                TournamentFormat.SINGLE_ELIMINATION, TournamentStatus.DRAFT,
                FIXED_NOW, null, new UserId("admin"), FIXED_NOW);
        GameDefinition gd = new GameDefinition(GameType.CHESS, "Scacchi", 2, 2, false, null, FIXED_NOW, FIXED_NOW);
        when(gameDefinitionRepository.findByGameType(GameType.CHESS)).thenReturn(Optional.of(gd));
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(inv -> inv.getArgument(0));

        TournamentDto dto = service.create(input, List.of("b-1", "b-2"));

        assertThat(dto.id()).isEqualTo("t-1");
        assertThat(dto.name()).isEqualTo("Test Cup");
        assertThat(dto.gameType()).isEqualTo(GameType.CHESS);
        assertThat(dto.teamBased()).isFalse();
        assertThat(dto.teamSize()).isEqualTo(1);
        assertThat(dto.status()).isEqualTo(TournamentStatus.DRAFT);
        assertThat(dto.startsAt()).isEqualTo(FIXED_NOW);
        assertThat(dto.endsAt()).isNull();
        assertThat(dto.buildings()).containsExactly("b-1", "b-2");
        assertThat(dto.participantsCount()).isEqualTo(0);

        ArgumentCaptor<Tournament> captor = ArgumentCaptor.forClass(Tournament.class);
        verify(tournamentRepository).save(captor.capture());
        Tournament saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(TournamentStatus.DRAFT);
        assertThat(saved.getFormat()).isEqualTo(TournamentFormat.SINGLE_ELIMINATION);
        assertThat(saved.getEndsAt()).isNull();
        assertThat(saved.getCreatedAt()).isEqualTo(FIXED_NOW);

        verify(tournamentBuildingRepository).saveAll(eq(new TournamentId("t-1")), eq(List.of("b-1", "b-2")));
    }

    @Test
    void create_throwsInvalidTournamentException_whenLessThanTwoBuildings() {
        Tournament input = new Tournament(
                new TournamentId("t-1"), "Test Cup", GameType.CHESS, false, 1,
                TournamentFormat.SINGLE_ELIMINATION, TournamentStatus.DRAFT,
                FIXED_NOW, null, new UserId("admin"), FIXED_NOW);

        assertThatThrownBy(() -> service.create(input, List.of("b-only")))
                .isInstanceOf(InvalidTournamentException.class)
                .hasMessageContaining("At least 2 buildings are required");

        verify(tournamentRepository, never()).save(any());
    }

    @Test
    void create_throwsInvalidTournamentException_whenTeamBasedButGameDisallowsTeams() {
        Tournament input = new Tournament(
                new TournamentId("t-1"), "Team Cup", GameType.CHESS, true, 2,
                TournamentFormat.SINGLE_ELIMINATION, TournamentStatus.DRAFT,
                FIXED_NOW, null, new UserId("admin"), FIXED_NOW);
        GameDefinition gd = new GameDefinition(GameType.CHESS, "Scacchi", 2, 2, false, null, FIXED_NOW, FIXED_NOW);
        when(gameDefinitionRepository.findByGameType(GameType.CHESS)).thenReturn(Optional.of(gd));

        assertThatThrownBy(() -> service.create(input, List.of("b-1", "b-2")))
                .isInstanceOf(InvalidTournamentException.class)
                .hasMessageContaining("does not allow team-based");
    }

    @Test
    void create_teamBasedWithTeamSize1_throwsInvalidTournament() {
        Tournament input = new Tournament(
                new TournamentId("t-1"), "Degenerate Team Cup", GameType.CHESS, true, 1,
                TournamentFormat.SINGLE_ELIMINATION, TournamentStatus.DRAFT,
                FIXED_NOW, null, new UserId("admin"), FIXED_NOW);
        GameDefinition gd = new GameDefinition(GameType.CHESS, "Scacchi", 2, 2, true, null, FIXED_NOW, FIXED_NOW);
        when(gameDefinitionRepository.findByGameType(GameType.CHESS)).thenReturn(Optional.of(gd));

        assertThatThrownBy(() -> service.create(input, List.of("b-1", "b-2")))
                .isInstanceOf(InvalidTournamentException.class)
                .hasMessageContaining("teamSize >= 2");

        verify(tournamentRepository, never()).save(any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // open()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void open_transitionsToOpenRegistration_whenStatusIsDraft() {
        TournamentId tid = new TournamentId("t-1");
        Tournament draft = new Tournament(
                tid, "Test Cup", GameType.CHESS, false, 1,
                TournamentFormat.SINGLE_ELIMINATION, TournamentStatus.DRAFT,
                FIXED_NOW, null, new UserId("admin"), FIXED_NOW);
        when(tournamentRepository.findById(tid)).thenReturn(Optional.of(draft));
        when(tournamentBuildingRepository.findByTournament(tid)).thenReturn(List.of("b-1", "b-2"));
        when(tournamentParticipantRepository.countByTournament(tid)).thenReturn(0L);

        TournamentDto dto = service.open(tid);

        assertThat(dto.id()).isEqualTo("t-1");
        assertThat(dto.status()).isEqualTo(TournamentStatus.OPEN_REGISTRATION);
        assertThat(dto.buildings()).containsExactly("b-1", "b-2");
        assertThat(dto.participantsCount()).isEqualTo(0);

        ArgumentCaptor<Tournament> captor = ArgumentCaptor.forClass(Tournament.class);
        verify(tournamentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TournamentStatus.OPEN_REGISTRATION);
    }

    @Test
    void open_throwsTournamentNotFoundException_whenMissing() {
        TournamentId tid = new TournamentId("t-1");
        when(tournamentRepository.findById(tid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.open(tid))
                .isInstanceOf(TournamentNotFoundException.class)
                .hasMessageContaining("Tournament not found");
    }

    @Test
    void open_throwsInvalidTournamentStateException_whenAlreadyOpen() {
        TournamentId tid = new TournamentId("t-1");
        Tournament open = new Tournament(
                tid, "Test Cup", GameType.CHESS, false, 1,
                TournamentFormat.SINGLE_ELIMINATION, TournamentStatus.OPEN_REGISTRATION,
                FIXED_NOW, null, new UserId("admin"), FIXED_NOW);
        when(tournamentRepository.findById(tid)).thenReturn(Optional.of(open));

        assertThatThrownBy(() -> service.open(tid))
                .isInstanceOf(InvalidTournamentStateException.class);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // cancel()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void cancel_transitionsToCancelled_whenStatusIsDraft() {
        TournamentId tid = new TournamentId("t-1");
        Tournament draft = new Tournament(
                tid, "Test Cup", GameType.CHESS, false, 1,
                TournamentFormat.SINGLE_ELIMINATION, TournamentStatus.DRAFT,
                FIXED_NOW, null, new UserId("admin"), FIXED_NOW);
        when(tournamentRepository.findById(tid)).thenReturn(Optional.of(draft));
        when(tournamentBuildingRepository.findByTournament(tid)).thenReturn(List.of("b-1", "b-2"));
        when(tournamentParticipantRepository.countByTournament(tid)).thenReturn(0L);

        TournamentDto dto = service.cancel(tid);

        assertThat(dto.id()).isEqualTo("t-1");
        assertThat(dto.status()).isEqualTo(TournamentStatus.CANCELLED);
        assertThat(dto.buildings()).containsExactly("b-1", "b-2");
        assertThat(dto.participantsCount()).isEqualTo(0);

        ArgumentCaptor<Tournament> captor = ArgumentCaptor.forClass(Tournament.class);
        verify(tournamentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TournamentStatus.CANCELLED);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // cancel() — BUG-CANCEL-PENDING regression: rejected cancel MUST emit a FAILED
    // return event so the Local admin_requests_local row transitions to FAILED
    // immediately (with the readable reason) instead of waiting 30 min for the
    // AdminRequestTimeoutService to surface a vague "TIMEOUT" card.
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Regression for BUG-CANCEL-PENDING (root cause B): cancelling a tournament
     * in a status that {@link Tournament#cancel()} does not admit (anything
     * other than DRAFT/OPEN_REGISTRATION) used to throw
     * {@link InvalidTournamentStateException} which propagated all the way out
     * of {@code SyncEventProcessor.processOne} and was swallowed by the poison-
     * isolation catch in {@code SyncReceiverService.receiveSyncPayload} → NO
     * return outbox event → Local admin_requests_local stayed PENDING for 30
     * min → FAILED with {@code "reason":"TIMEOUT"}.
     *
     * <p>After the fix the use case catches the rejection and emits a single
     * {@code TOURNAMENT_SUMMARY_UPSERTED} return outbox event carrying a
     * non-null {@code errorMessage} so the Local
     * {@code TournamentSummarySyncService} closes the admin-request as FAILED
     * with the ACTUAL reason. The repository is NOT mutated, and the method
     * returns {@code null} instead of throwing.</p>
     */
    @Test
    void cancel_emitsFailedReturnEventAndDoesNotThrow_whenStatusIsCompleted() throws Exception {
        TournamentId tid = new TournamentId("t-completed");
        Tournament completed = new Tournament(
                tid, "Test Cup", GameType.CHESS, false, 1,
                TournamentFormat.SINGLE_ELIMINATION, TournamentStatus.COMPLETED,
                FIXED_NOW, FIXED_NOW, new UserId("admin"), FIXED_NOW);
        when(tournamentRepository.findById(tid)).thenReturn(Optional.of(completed));
        when(tournamentBuildingRepository.findByTournament(tid)).thenReturn(List.of("b-1", "b-2"));
        when(tournamentParticipantRepository.countByTournament(tid)).thenReturn(2L);

        TournamentDto result = service.cancel(tid, "request-id-completed");

        // NO exception thrown, NO tournament mutation, return null DTO.
        assertThat(result).isNull();
        verify(tournamentRepository, never()).save(any(Tournament.class));

        // Exactly one outbox event saved — the FAILED return event.
        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());
        OutboxEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("TOURNAMENT_SUMMARY_UPSERTED");
        TournamentSummaryEventDto payload = objectMapper.readValue(event.getPayload(),
                TournamentSummaryEventDto.class);
        assertThat(payload.originatingRequestId()).isEqualTo("request-id-completed");
        assertThat(payload.tournamentId()).isEqualTo("t-completed");
        // Unchanged snapshot — the tournament was NOT mutated.
        assertThat(payload.status()).isEqualTo(TournamentStatus.COMPLETED);
        assertThat(payload.deleted()).isFalse();
        // The readable rejection reason is carried back to the Local.
        assertThat(payload.errorMessage()).isEqualTo("Cannot cancel from status COMPLETED");
    }

    @Test
    void cancel_emitsFailedReturnEventAndDoesNotThrow_whenStatusIsInProgress() throws Exception {
        TournamentId tid = new TournamentId("t-in-progress");
        Tournament inProgress = new Tournament(
                tid, "Active Cup", GameType.CHESS, false, 1,
                TournamentFormat.SINGLE_ELIMINATION, TournamentStatus.IN_PROGRESS,
                FIXED_NOW, null, new UserId("admin"), FIXED_NOW);
        when(tournamentRepository.findById(tid)).thenReturn(Optional.of(inProgress));
        when(tournamentBuildingRepository.findByTournament(tid)).thenReturn(List.of("b-1"));
        when(tournamentParticipantRepository.countByTournament(tid)).thenReturn(4L);

        TournamentDto result = service.cancel(tid, "request-id-in-progress");

        assertThat(result).isNull();
        verify(tournamentRepository, never()).save(any(Tournament.class));

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());
        OutboxEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("TOURNAMENT_SUMMARY_UPSERTED");
        TournamentSummaryEventDto payload = objectMapper.readValue(event.getPayload(),
                TournamentSummaryEventDto.class);
        assertThat(payload.originatingRequestId()).isEqualTo("request-id-in-progress");
        assertThat(payload.tournamentId()).isEqualTo("t-in-progress");
        assertThat(payload.status()).isEqualTo(TournamentStatus.IN_PROGRESS); // snapshot unchanged
        assertThat(payload.errorMessage()).isEqualTo("Cannot cancel from status IN_PROGRESS");
    }

    @Test
    void cancel_emitsTombstoneFailedReturnEventAndDoesNotThrow_whenTournamentNotFound() throws Exception {
        TournamentId tid = new TournamentId("t-missing");
        when(tournamentRepository.findById(tid)).thenReturn(Optional.empty());

        TournamentDto result = service.cancel(tid, "request-id-404");

        assertThat(result).isNull();
        verify(tournamentRepository, never()).save(any(Tournament.class));

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());
        OutboxEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("TOURNAMENT_SUMMARY_UPSERTED");
        TournamentSummaryEventDto payload = objectMapper.readValue(event.getPayload(),
                TournamentSummaryEventDto.class);
        assertThat(payload.originatingRequestId()).isEqualTo("request-id-404");
        assertThat(payload.tournamentId()).isEqualTo("t-missing");
        // Tournament missing → emit a tombstone (deleteById is a no-op when the
        // local projection row does not exist; the markFailed hook still closes
        // the admin-request).
        assertThat(payload.deleted()).isTrue();
        assertThat(payload.errorMessage()).isEqualTo("Tournament not found: t-missing");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // getById()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void getById_returnsEmpty_whenMissing() {
        TournamentId tid = new TournamentId("t-1");
        when(tournamentRepository.findById(tid)).thenReturn(Optional.empty());

        Optional<TournamentDto> result = service.getById(tid);

        assertThat(result).isEmpty();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // findByStatus()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void findByStatus_returnsMappedDtos() {
        TournamentId tid = new TournamentId("t-1");
        Tournament draft = new Tournament(
                tid, "Test Cup", GameType.CHESS, false, 1,
                TournamentFormat.SINGLE_ELIMINATION, TournamentStatus.DRAFT,
                FIXED_NOW, null, new UserId("admin"), FIXED_NOW);
        when(tournamentRepository.findByStatus(TournamentStatus.DRAFT)).thenReturn(List.of(draft));
        when(tournamentBuildingRepository.findByTournament(tid)).thenReturn(List.of("b-1", "b-2"));
        when(tournamentParticipantRepository.countByTournament(tid)).thenReturn(0L);

        List<TournamentDto> result = service.findByStatus(TournamentStatus.DRAFT);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("t-1");
        assertThat(result.get(0).status()).isEqualTo(TournamentStatus.DRAFT);
        assertThat(result.get(0).buildings()).containsExactly("b-1", "b-2");
        assertThat(result.get(0).participantsCount()).isEqualTo(0);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // update() — §7.A.1
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void update_persistsMutatedDraft_whenStatusIsDraft() throws Exception {
        TournamentId tid = new TournamentId("t1");
        Tournament draft = new Tournament(
                tid, "Old Name", GameType.CHESS, false, 1,
                TournamentFormat.SINGLE_ELIMINATION, TournamentStatus.DRAFT,
                FIXED_NOW, null, new UserId("admin"), FIXED_NOW);
        when(tournamentRepository.findById(tid)).thenReturn(Optional.of(draft));
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(inv -> inv.getArgument(0));

        TournamentDto dto = service.update(tid, "New Name",
                Instant.parse("2026-08-01T10:00:00Z"), List.of("b1", "b2", "b3"), null);

        ArgumentCaptor<Tournament> captor = ArgumentCaptor.forClass(Tournament.class);
        verify(tournamentRepository).save(captor.capture());
        Tournament saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("New Name");
        assertThat(saved.getStartsAt()).isEqualTo(Instant.parse("2026-08-01T10:00:00Z"));
        assertThat(saved.getStatus()).isEqualTo(TournamentStatus.DRAFT);

        verify(tournamentBuildingRepository).deleteByTournament(tid);
        verify(tournamentBuildingRepository).saveAll(eq(tid), eq(List.of("b1", "b2", "b3")));

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());
        OutboxEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("TOURNAMENT_SUMMARY_UPSERTED");
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getSentAt()).isNull();
        TournamentSummaryEventDto payload = objectMapper.readValue(event.getPayload(),
                TournamentSummaryEventDto.class);
        assertThat(payload.deleted()).isFalse();
        assertThat(payload.tournamentId()).isEqualTo("t1");
        assertThat(payload.name()).isEqualTo("New Name");
        assertThat(payload.buildingIds()).containsExactly("b1", "b2", "b3");

        assertThat(dto.name()).isEqualTo("New Name");
        assertThat(dto.buildings()).containsExactly("b1", "b2", "b3");
    }

    @Test
    void update_throwsInvalidTournamentStateException_whenStatusIsOpenRegistration() {
        TournamentId tid = new TournamentId("t1");
        Tournament open = new Tournament(
                tid, "Old Name", GameType.CHESS, false, 1,
                TournamentFormat.SINGLE_ELIMINATION, TournamentStatus.OPEN_REGISTRATION,
                FIXED_NOW, null, new UserId("admin"), FIXED_NOW);
        when(tournamentRepository.findById(tid)).thenReturn(Optional.of(open));

        assertThatThrownBy(() -> service.update(tid, "New Name", FIXED_NOW, List.of("b1", "b2"), null))
                .isInstanceOf(InvalidTournamentStateException.class);

        verify(outboxEventRepository, never()).save(any());
        verify(tournamentRepository, never()).save(any(Tournament.class));
        verify(tournamentBuildingRepository, never()).deleteByTournament(any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // delete() — §7.A.1
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void delete_removesTournamentAndBuildings_whenStatusIsDraft() throws Exception {
        TournamentId tid = new TournamentId("t1");
        Tournament draft = new Tournament(
                tid, "Old Name", GameType.CHESS, false, 1,
                TournamentFormat.SINGLE_ELIMINATION, TournamentStatus.DRAFT,
                FIXED_NOW, null, new UserId("admin"), FIXED_NOW);
        when(tournamentRepository.findById(tid)).thenReturn(Optional.of(draft));
        when(tournamentBuildingRepository.findByTournament(tid)).thenReturn(List.of("b1", "b2"));

        service.delete(tid, null);

        verify(tournamentBuildingRepository).deleteByTournament(tid);
        verify(tournamentRepository).deleteById(tid);

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());
        OutboxEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("TOURNAMENT_SUMMARY_UPSERTED");
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        TournamentSummaryEventDto payload = objectMapper.readValue(event.getPayload(),
                TournamentSummaryEventDto.class);
        assertThat(payload.deleted()).isTrue();
        assertThat(payload.tournamentId()).isEqualTo("t1");
        assertThat(payload.buildingIds()).containsExactly("b1", "b2");
    }

    @Test
    void delete_throwsInvalidTournamentStateException_whenStatusIsInProgress() {
        TournamentId tid = new TournamentId("t1");
        Tournament inProgress = new Tournament(
                tid, "Old Name", GameType.CHESS, false, 1,
                TournamentFormat.SINGLE_ELIMINATION, TournamentStatus.IN_PROGRESS,
                FIXED_NOW, null, new UserId("admin"), FIXED_NOW);
        when(tournamentRepository.findById(tid)).thenReturn(Optional.of(inProgress));

        assertThatThrownBy(() -> service.delete(tid, null))
                .isInstanceOf(InvalidTournamentStateException.class);

        verify(outboxEventRepository, never()).save(any());
        verify(tournamentRepository, never()).deleteById(any());
        verify(tournamentBuildingRepository, never()).deleteByTournament(any());
    }
}