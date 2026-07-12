package com.gameplatform.central.application.service;

import com.gameplatform.central.domain.exception.InvalidTournamentException;
import com.gameplatform.central.domain.exception.InvalidTournamentStateException;
import com.gameplatform.central.domain.exception.TournamentNotFoundException;
import com.gameplatform.central.domain.model.GameDefinition;
import com.gameplatform.central.domain.model.Tournament;
import com.gameplatform.central.domain.ports.out.GameDefinitionRepository;
import com.gameplatform.central.domain.ports.out.TournamentBuildingRepository;
import com.gameplatform.central.domain.ports.out.TournamentParticipantRepository;
import com.gameplatform.central.domain.ports.out.TournamentRepository;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentFormat;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.TournamentDto;
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
 * cases. Pure Mockito (no Spring context); uses a real {@link Clock#fixed} so
 * {@code createdAt} is deterministic.
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

    private TournamentService service;

    @BeforeEach
    void setUp() {
        service = new TournamentService(
                tournamentRepository, tournamentBuildingRepository,
                tournamentParticipantRepository, gameDefinitionRepository, clock);
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
}