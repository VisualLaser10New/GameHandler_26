package com.gameplatform.central.infrastructure.adapters.in.rest;

import com.gameplatform.central.domain.exception.InvalidTournamentException;
import com.gameplatform.central.domain.ports.in.ListTournamentParticipantsUseCase;
import com.gameplatform.central.domain.ports.in.RegisterTournamentParticipantUseCase;
import com.gameplatform.central.domain.ports.in.UnregisterTournamentParticipantUseCase;
import com.gameplatform.central.infrastructure.security.CurrentUserService;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.RegisterTournamentParticipantDto;
import com.gameplatform.shared.dto.TournamentParticipantDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST adapter exposing the FASE 4 tournament registration endpoints (PIANO_UTENTI_TORNEI.md
 * §3.6 {@code TournamentRegistrationController}). Registration paths require
 * {@code PLAYER}; listing is {@code authenticated}.
 *
 * <p>The captain is the authenticated principal (locked decision C.4 — no
 * {@code captain} body field); the service validates that the captain is
 * contained in {@code teamMembers} and that the list has size {@code tournament.teamSize}.
 */
@RestController
@RequestMapping("/api/tournaments/{id}/participants")
public class TournamentRegistrationController {

    private final RegisterTournamentParticipantUseCase registerUseCase;
    private final UnregisterTournamentParticipantUseCase unregisterUseCase;
    private final ListTournamentParticipantsUseCase listUseCase;
    private final CurrentUserService currentUserService;

    /**
     * Costruisce il controller iniettando i casi d'uso e il servizio utente corrente.
     *
     * @param registerUseCase   caso d'uso per registrare un partecipante, non {@code null}
     * @param unregisterUseCase caso d'uso per annullare una registrazione, non {@code null}
     * @param listUseCase       caso d'uso per elencare i partecipanti, non {@code null}
     * @param currentUserService servizio per la risoluzione dell'utente autenticato, non {@code null}
     */
    public TournamentRegistrationController(RegisterTournamentParticipantUseCase registerUseCase,
                                            UnregisterTournamentParticipantUseCase unregisterUseCase,
                                            ListTournamentParticipantsUseCase listUseCase,
                                            CurrentUserService currentUserService) {
        this.registerUseCase = registerUseCase;
        this.unregisterUseCase = unregisterUseCase;
        this.listUseCase = listUseCase;
        this.currentUserService = currentUserService;
    }

    /**
     * Registra un partecipante al torneo identificato dall'identificativo fornito.
     *
     * <p>L'operazione richiede il ruolo {@code PLAYER} o {@code PLATFORM_ADMIN}. Il capitano
     * corrisponde all'utente autenticato e deve essere contenuto nella lista dei membri del team.</p>
     *
     * @param id      identificativo del torneo a cui iscriversi, non {@code null} né vuoto
     * @param request dto di richiesta con nome del team e membri, validato tramite {@code @Valid}; non {@code null}
     * @return {@link ResponseEntity} con stato {@code 200 OK} e il {@link TournamentParticipantDto} registrato
     * @throws InvalidTournamentException se l'utente autenticato non è risolvibile, o i dati non sono validi (mappato a {@code 400})
     * @throws com.gameplatform.central.domain.exception.TournamentNotFoundException se il torneo non esiste (mappato a {@code 404})
     * @throws com.gameplatform.central.domain.exception.TournamentRegistrationClosedException se l'iscrizione è chiusa (mappato a {@code 409})
     * @throws com.gameplatform.central.domain.exception.DuplicateTournamentParticipantException se il partecipante è già registrato (mappato a {@code 409})
     * @throws jakarta.validation.ValidationException se il body non supera i vincoli di validazione (mappato a {@code 400})
     * @see GlobalExceptionHandler
     */
    @PostMapping
    @PreAuthorize("hasRole('PLAYER') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<TournamentParticipantDto> register(@PathVariable String id,
                                                             @Valid @RequestBody RegisterTournamentParticipantDto request) {
        UserId captainId = currentUserService.getCurrentUserId()
                .orElseThrow(() -> new InvalidTournamentException("Authenticated user could not be resolved"));
        return ResponseEntity.ok(registerUseCase.register(new TournamentId(id), captainId, request.teamName(), request.teamMembers()));
    }

    /**
     * Annulla la registrazione dell'utente autenticato al torneo indicato.
     *
     * <p>L'operazione richiede il ruolo {@code PLAYER} o {@code PLATFORM_ADMIN}.</p>
     *
     * @param id identificativo del torneo da cui annullare l'iscrizione, non {@code null} né vuoto
     * @return {@link ResponseEntity} con stato {@code 204 No Content} e corpo vuoto
     * @throws InvalidTournamentException se l'utente autenticato non è risolvibile (mappato a {@code 400})
     * @throws com.gameplatform.central.domain.exception.TournamentNotFoundException se il torneo non esiste (mappato a {@code 404})
     * @see GlobalExceptionHandler
     */
    @DeleteMapping
    @PreAuthorize("hasRole('PLAYER') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Void> unregister(@PathVariable String id) {
        UserId uid = currentUserService.getCurrentUserId()
                .orElseThrow(() -> new InvalidTournamentException("Authenticated user could not be resolved"));
        unregisterUseCase.unregister(new TournamentId(id), uid);
        return ResponseEntity.noContent().build();
    }

    /**
     * Restituisce l'elenco dei partecipanti al torneo identificato dall'identificativo fornito.
     *
     * <p>L'operazione è disponibile a qualsiasi principal autenticato.</p>
     *
     * @param id identificativo del torneo di cui elencare i partecipanti, non {@code null} né vuoto
     * @return {@link ResponseEntity} con stato {@code 200 OK} e la lista di {@link TournamentParticipantDto};
     *         la lista è vuota se non vi sono partecipanti registrati
     * @throws com.gameplatform.central.domain.exception.TournamentNotFoundException se il torneo non esiste (mappato a {@code 404})
     * @see GlobalExceptionHandler
     */
    @GetMapping
    public ResponseEntity<List<TournamentParticipantDto>> list(@PathVariable String id) {
        return ResponseEntity.ok(listUseCase.listParticipants(new TournamentId(id)));
    }
}
