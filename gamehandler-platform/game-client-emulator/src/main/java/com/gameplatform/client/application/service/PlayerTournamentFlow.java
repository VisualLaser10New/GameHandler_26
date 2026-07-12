package com.gameplatform.client.application.service;

import com.gameplatform.client.infrastructure.rest.ApiClient;
import com.gameplatform.shared.dto.AdminRequestDto;
import com.gameplatform.shared.dto.GameSessionDto;
import com.gameplatform.shared.dto.RegisterTournamentParticipantDto;
import com.gameplatform.shared.dto.TournamentDetailDto;
import com.gameplatform.shared.dto.TournamentMatchDto;
import com.gameplatform.shared.dto.TournamentStandingDto;
import com.gameplatform.shared.dto.TournamentSummaryDto;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Application service that orchestrates all the player-side tournament
 * flows of the Game Client Emulator (PIANO §7.C line 742).
 * <p>
 * Wraps the {@link ApiClient} and exposes async operations that the
 * JavaFX {@code TournamentsView} consumes:
 * <ul>
 *   <li>{@code GET /api/tournaments[?status=]} — list</li>
 *   <li>{@code GET /api/tournaments/{id}} — detail (aggregated)</li>
 *   <li>{@code GET /api/tournaments/{id}/standings} — standings</li>
 *   <li>{@code GET /api/tournaments/{id}/matches} — bracket</li>
 *   <li>{@code GET /api/tournaments/{id}/participants} — participants</li>
 *   <li>{@code POST /api/tournaments/{id}/participants} — registration (PLAYER async-write)</li>
 *   <li>{@code GET /api/players/tournaments/me/matches} — current user's scheduled matches</li>
 *   <li>{@code POST /api/players/tournaments/matches/{matchId}/start} — start a match</li>
 * </ul>
 * Each method returns a {@link CompletableFuture}; the calling view is
 * expected to marshal the callbacks back onto the JavaFX Application
 * Thread via {@code Platform.runLater}.
 *
 * <p>This service is a thin orchestrator — it does not keep any client
 * state. All session context (token, userId, roles, buildings) is held
 * by {@link com.gameplatform.client.infrastructure.security.HttpClientHelper}
 * and implicitly attached to each outbound request by the {@link ApiClient}.
 */
public class PlayerTournamentFlow {

    private final ApiClient api;

    public PlayerTournamentFlow() {
        this(ApiClient.instance());
    }

    /** Test hook — allows injecting a customised {@link ApiClient} for stubbed flows. */
    public PlayerTournamentFlow(ApiClient api) {
        this.api = api;
    }

    /** {@code GET /api/tournaments} — list every available tournament (optionally filtered by status). */
    public CompletableFuture<List<TournamentSummaryDto>> listTournaments() {
        return api.get("/api/tournaments", new TypeReference<List<TournamentSummaryDto>>() {});
    }

    /** {@code GET /api/tournaments?status={STATUS}} — filtered by a {@link com.gameplatform.shared.domain.model.TournamentStatus} literal. */
    public CompletableFuture<List<TournamentSummaryDto>> listTournaments(String statusFilter) {
        String suffix = statusFilter == null ? "" : "status=" + statusFilter;
        return api.get("/api/tournaments", suffix, new TypeReference<List<TournamentSummaryDto>>() {});
    }

    /** {@code GET /api/tournaments/{id}} — aggregated detail. */
    public CompletableFuture<TournamentDetailDto> getTournament(String tournamentId) {
        return api.get("/api/tournaments/" + tournamentId, TournamentDetailDto.class);
    }

    /** {@code GET /api/tournaments/{id}/standings}. */
    public CompletableFuture<List<TournamentStandingDto>> getStandings(String tournamentId) {
        return api.get("/api/tournaments/" + tournamentId + "/standings",
                new TypeReference<List<TournamentStandingDto>>() {});
    }

    /** {@code GET /api/tournaments/{id}/matches} — the bracket. */
    public CompletableFuture<List<TournamentMatchDto>> getMatches(String tournamentId) {
        return api.get("/api/tournaments/" + tournamentId + "/matches",
                new TypeReference<List<TournamentMatchDto>>() {});
    }

    /** {@code GET /api/tournaments/{id}/participants}. */
    public CompletableFuture<List<com.gameplatform.shared.dto.TournamentParticipantViewDto>> getParticipants(String tournamentId) {
        return api.get("/api/tournaments/" + tournamentId + "/participants",
                new TypeReference<List<com.gameplatform.shared.dto.TournamentParticipantViewDto>>() {});
    }

    /**
     * {@code POST /api/tournaments/{id}/participants} — registers the
     * authenticated PLAYER (or the team body) into the tournament. The
     * Local writes an outbox {@code PARTICIPANT_REGISTER_REQUESTED}
     * event and returns an {@link AdminRequestDto} with
     * {@code status=PENDING}; the Central processes the request
     * asynchronously and replicates the participants back to the
     * Local (PIANO §7.B W6, ~10 min round-trip).
     */
    public CompletableFuture<AdminRequestDto> register(String tournamentId, RegisterTournamentParticipantDto body) {
        if (body == null) {
            // POST with empty body — the Local accepts `required = false`
            // so an individual self-registration works with no payload.
            return api.postEmpty("/api/tournaments/" + tournamentId + "/participants", AdminRequestDto.class);
        }
        return api.post("/api/tournaments/" + tournamentId + "/participants", body, AdminRequestDto.class);
    }

    /** Convenience: individual self-registration with no team body. */
    public CompletableFuture<AdminRequestDto> registerSelf(String tournamentId) {
        return register(tournamentId, null);
    }

    /** {@code GET /api/players/tournaments/me/matches} — the authenticated user's scheduled matches. */
    public CompletableFuture<List<TournamentMatchDto>> myMatches() {
        return api.get("/api/players/tournaments/me/matches",
                new TypeReference<List<TournamentMatchDto>>() {});
    }

    /**
     * {@code POST /api/players/tournaments/matches/{matchId}/start} —
     * starts the game session bound to a SCHEDULED tournament match. The
     * Local Server returns the freshly created {@link GameSessionDto}
     * (HTTP 201) once validation passes.
     *
     * @param matchId the tournament match id (path-variable)
     * @param gameId optional gameId request param (used when the local
     *              replicated match does not yet have its own
     *              {@code gameId} assigned by the central drain)
     */
    public CompletableFuture<GameSessionDto> startMatch(String matchId, String gameId) {
        String path = "/api/players/tournaments/matches/" + matchId + "/start";
        if (gameId != null && !gameId.isBlank()) {
            path = path + "?gameId=" + gameId;
        }
        return api.postEmpty(path, GameSessionDto.class);
    }
}