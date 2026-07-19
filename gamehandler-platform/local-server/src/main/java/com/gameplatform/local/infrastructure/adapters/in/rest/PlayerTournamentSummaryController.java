package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.application.service.GetTournamentDetailService;
import com.gameplatform.local.application.service.ListTournamentSummariesService;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.dto.TournamentDetailDto;
import com.gameplatform.shared.dto.TournamentMatchDto;
import com.gameplatform.shared.dto.TournamentParticipantViewDto;
import com.gameplatform.shared.dto.TournamentStandingDto;
import com.gameplatform.shared.dto.TournamentSummaryDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Player read endpoints (PIANO §7.B): exposes the replicated tournament
 * data to authenticated clients (any authenticated user — the brief says
 * {@code isAuthenticated()}), backed by the local replicas
 * {@code tournaments_summary_local}, {@code tournament_standings_local},
 * {@code tournament_matches_local} and {@code tournament_participants_local}.
 * Aggregated detail view is served by {@link GetTournamentDetailService};
 * the list view by {@link ListTournamentSummariesService}; the sub-resource
 * endpoints ({@code /standings}, {@code /matches}, {@code /participants})
 * reuse the aggregated detail view and project out the relevant list.
 *
 * <p>Spring Security method security is intentionally NOT declared at
 * class level via {@code @PreAuthorize} because the spec requires
 * {@code isAuthenticated()} (already the default catch-all in
 * {@code SecurityConfig}: {@code .anyRequest().authenticated()}).</p>
 */
@RestController
@RequestMapping("/api/tournaments")
public class PlayerTournamentSummaryController {

    private final ListTournamentSummariesService listTournamentSummariesService;
    private final GetTournamentDetailService getTournamentDetailService;

    /**
     * Costruisce il controller con i servizi per la lista e il dettaglio
     * dei tornei.
     *
     * @param listTournamentSummariesService servizio per la lista dei riepiloghi torneo
     * @param getTournamentDetailService servizio per il dettaglio del torneo
     */
    public PlayerTournamentSummaryController(ListTournamentSummariesService listTournamentSummariesService,
                                             GetTournamentDetailService getTournamentDetailService) {
        this.listTournamentSummariesService = listTournamentSummariesService;
        this.getTournamentDetailService = getTournamentDetailService;
    }

    /**
     * Restituisce l'elenco dei tornei, opzionalmente filtrati per stato.
     *
     * @param status filtro opzionale per stato del torneo
     * @return una {@link ResponseEntity} con la lista di {@link TournamentSummaryDto}
     * @throws IllegalArgumentException se il valore dello status non è valido
     */
    @GetMapping
    public ResponseEntity<List<TournamentSummaryDto>> listTournaments(
            @RequestParam(name = "status", required = false) String status) {
        TournamentStatus filter = null;
        if (status != null && !status.isBlank()) {
            try {
                filter = TournamentStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Unknown status: '" + status
                        + "'. Valid values are: " + java.util.Arrays.toString(TournamentStatus.values()));
            }
        }
        return ResponseEntity.ok(listTournamentSummariesService.listSummaries(filter));
    }

    /**
     * Restituisce il dettaglio completo di un torneo, incluse classifiche,
     * partite e partecipanti.
     *
     * @param id l'identificativo del torneo
     * @return una {@link ResponseEntity} con il {@link TournamentDetailDto},
     *         o status 404 se non trovato
     */
    @GetMapping("/{id}")
    public ResponseEntity<TournamentDetailDto> getTournament(@PathVariable String id) {
        return getTournamentDetailService.getDetail(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    /**
     * Restituisce la classifica di un torneo.
     *
     * @param id l'identificativo del torneo
     * @return una {@link ResponseEntity} con la lista di {@link TournamentStandingDto},
     *         o status 404 se non trovato
     */
    @GetMapping("/{id}/standings")
    public ResponseEntity<List<TournamentStandingDto>> getTournamentStandings(@PathVariable String id) {
        return getTournamentDetailService.getDetail(id)
                .map(detail -> ResponseEntity.ok(detail.standings()))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    /**
     * Restituisce le partite di un torneo.
     *
     * @param id l'identificativo del torneo
     * @return una {@link ResponseEntity} con la lista di {@link TournamentMatchDto},
     *         o status 404 se non trovato
     */
    @GetMapping("/{id}/matches")
    public ResponseEntity<List<TournamentMatchDto>> getTournamentMatches(@PathVariable String id) {
        return getTournamentDetailService.getDetail(id)
                .map(detail -> ResponseEntity.ok(detail.matches()))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    /**
     * Restituisce i partecipanti di un torneo.
     *
     * @param id l'identificativo del torneo
     * @return una {@link ResponseEntity} con la lista di {@link TournamentParticipantViewDto},
     *         o status 404 se non trovato
     */
    @GetMapping("/{id}/participants")
    public ResponseEntity<List<TournamentParticipantViewDto>> getTournamentParticipants(@PathVariable String id) {
        return getTournamentDetailService.getDetail(id)
                .map(detail -> ResponseEntity.ok(detail.participants()))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}