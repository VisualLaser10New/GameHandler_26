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
 * Servizio applicativo che orchestra i flussi torneo lato giocatore del Game Client Emulator.
 * Espone operazioni asincrone per la consultazione dei tornei, la registrazione, la
 * visualizzazione di classifiche e incontri, e l'avvio delle partite. Ogni metodo restituisce
 * un {@link CompletableFuture}; la vista chiamante deve eseguire il marshalling sul thread
 * JavaFX Application tramite {@code Platform.runLater}.
 */
public class PlayerTournamentFlow {

    private final ApiClient api;

    /**
     * Costruisce un nuovo flusso torneo utilizzando l'istanza singleton di {@link ApiClient}.
     */
    public PlayerTournamentFlow() {
        this(ApiClient.instance());
    }

    /**
     * Costruisce un nuovo flusso torneo con un {@link ApiClient} personalizzato.
     * Utilizzato per test che richiedono un client mockato o configurato.
     *
     * @param api l'istanza di ApiClient da utilizzare, non null
     */
    public PlayerTournamentFlow(ApiClient api) {
        this.api = api;
    }

    /**
     * Recupera l'elenco di tutti i tornei disponibili.
     *
     * @return un CompletableFuture contenente la lista dei riepiloghi dei tornei
     */
    public CompletableFuture<List<TournamentSummaryDto>> listTournaments() {
        return api.get("/api/tournaments", new TypeReference<List<TournamentSummaryDto>>() {});
    }

    /**
     * Recupera l'elenco dei tornei disponibili, filtrati per stato.
     *
     * @param statusFilter il filtro per stato del torneo, può essere null per ottenere tutti i tornei
     * @return un CompletableFuture contenente la lista dei riepiloghi dei tornei filtrati
     */
    public CompletableFuture<List<TournamentSummaryDto>> listTournaments(String statusFilter) {
        String suffix = statusFilter == null ? "" : "status=" + statusFilter;
        return api.get("/api/tournaments", suffix, new TypeReference<List<TournamentSummaryDto>>() {});
    }

    /**
     * Recupera il dettaglio aggregato di un torneo specifico.
     *
     * @param tournamentId l'identificativo del torneo, non null
     * @return un CompletableFuture contenente il dettaglio del torneo
     */
    public CompletableFuture<TournamentDetailDto> getTournament(String tournamentId) {
        return api.get("/api/tournaments/" + tournamentId, TournamentDetailDto.class);
    }

    /**
     * Recupera la classifica di un torneo specifico.
     *
     * @param tournamentId l'identificativo del torneo, non null
     * @return un CompletableFuture contenente la lista delle posizioni in classifica
     */
    public CompletableFuture<List<TournamentStandingDto>> getStandings(String tournamentId) {
        return api.get("/api/tournaments/" + tournamentId + "/standings",
                new TypeReference<List<TournamentStandingDto>>() {});
    }

    /**
     * Recupera il tabellone degli incontri di un torneo specifico.
     *
     * @param tournamentId l'identificativo del torneo, non null
     * @return un CompletableFuture contenente la lista degli incontri del torneo
     */
    public CompletableFuture<List<TournamentMatchDto>> getMatches(String tournamentId) {
        return api.get("/api/tournaments/" + tournamentId + "/matches",
                new TypeReference<List<TournamentMatchDto>>() {});
    }

    /**
     * Recupera l'elenco dei partecipanti a un torneo specifico.
     *
     * @param tournamentId l'identificativo del torneo, non null
     * @return un CompletableFuture contenente la lista dei partecipanti
     */
    public CompletableFuture<List<com.gameplatform.shared.dto.TournamentParticipantViewDto>> getParticipants(String tournamentId) {
        return api.get("/api/tournaments/" + tournamentId + "/participants",
                new TypeReference<List<com.gameplatform.shared.dto.TournamentParticipantViewDto>>() {});
    }

    /**
     * Registra il giocatore autenticato (o il team) al torneo specificato.
     * Il server locale scrive un evento di outbox {@code PARTICIPANT_REGISTER_REQUESTED}
     * e restituisce un {@link AdminRequestDto} con stato {@code PENDING};
     * il server centrale elabora la richiesta in modo asincrono.
     *
     * @param tournamentId l'identificativo del torneo, non null
     * @param body         i dati di registrazione del partecipante, può essere null per
     *                     auto-registrazione individuale senza corpo della richiesta
     * @return un CompletableFuture contenente la richiesta amministrativa con stato PENDING
     */
    public CompletableFuture<AdminRequestDto> register(String tournamentId, RegisterTournamentParticipantDto body) {
        if (body == null) {
            // POST with empty body — the Local accepts `required = false`
            // so an individual self-registration works with no payload.
            return api.postEmpty("/api/tournaments/" + tournamentId + "/participants", AdminRequestDto.class);
        }
        return api.post("/api/tournaments/" + tournamentId + "/participants", body, AdminRequestDto.class);
    }

    /**
     * Auto-registrazione individuale al torneo specificato, senza corpo della richiesta.
     *
     * @param tournamentId l'identificativo del torneo, non null
     * @return un CompletableFuture contenente la richiesta amministrativa con stato PENDING
     * @see #register(String, RegisterTournamentParticipantDto)
     */
    public CompletableFuture<AdminRequestDto> registerSelf(String tournamentId) {
        return register(tournamentId, null);
    }

    /**
     * Recupera gli incontri schedulati dell'utente autenticato.
     *
     * @return un CompletableFuture contenente la lista degli incontri dell'utente
     */
    public CompletableFuture<List<TournamentMatchDto>> myMatches() {
        return api.get("/api/players/tournaments/me/matches",
                new TypeReference<List<TournamentMatchDto>>() {});
    }

    /**
     * Avvia la sessione di gioco associata a un incontro torneo schedulato.
     * Il server locale restituisce il {@link GameSessionDto} creato (HTTP 201)
     * una volta superata la validazione.
     *
     * @param matchId l'identificativo dell'incontro torneo, non null
     * @param gameId  parametro opzionale {@code gameId} nella richiesta, utilizzato quando
     *                l'incontro replicato localmente non ha ancora un {@code gameId}
     *                assegnato dal drain centrale; può essere null o vuoto
     * @return un CompletableFuture contenente il DTO della sessione di gioco creata
     */
    public CompletableFuture<GameSessionDto> startMatch(String matchId, String gameId) {
        String path = "/api/players/tournaments/matches/" + matchId + "/start";
        if (gameId != null && !gameId.isBlank()) {
            path = path + "?gameId=" + gameId;
        }
        return api.postEmpty(path, GameSessionDto.class);
    }
}