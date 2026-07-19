package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.domain.ports.in.SyncUsersUseCase;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.dto.UserSyncAckDto;
import com.gameplatform.shared.dto.UserSyncDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Local-server REST endpoints exposed for central → local synchronisation.
 *
 * <p>The base path is {@code /internal/users}, so:</p>
 * <ul>
 *   <li>{@link #syncUsers} → {@code PUT /internal/users/sync} (M3 contract —
 *       per-user ack list, poison users do not abort the batch);</li>
 *   <li>{@link #replicatedUsersCount} → {@code GET /internal/users/count}
 *       (M4 — returns the count of {@code replicated_users} rows; queried by
 *       the central {@code UserReplicationReconciliationService} every hour).</li>
 * </ul>
 *
 * <p>Both endpoints are covered by {@link com.gameplatform.local.infrastructure.security.InternalApiKeyFilter}
 * because the filter intercepts every path starting with {@code /internal/}
 * and validates the {@code X-Internal-Api-Key} header. A request without the
 * header (or with an invalid key) is rejected with 401 BEFORE reaching the
 * controller — so the count endpoint inherits the same 403-without-api-key
 * contract as the sync endpoint without any controller-level security code.</p>
 */
@RestController
@RequestMapping("/internal/users")
public class InternalSyncController {

    private final SyncUsersUseCase syncUsersUseCase;
    private final UserRepository userRepository;

    /**
     * Costruisce il controller con il caso d'uso per la sincronizzazione
     * utenti e il repository degli utenti.
     *
     * @param syncUsersUseCase caso d'uso per la sincronizzazione degli utenti
     * @param userRepository repository degli utenti per il conteggio
     */
    public InternalSyncController(SyncUsersUseCase syncUsersUseCase, UserRepository userRepository) {
        this.syncUsersUseCase = syncUsersUseCase;
        this.userRepository = userRepository;
    }

    /**
     * Sincronizza un batch di utenti dal sistema centrale. La risposta
     * contiene gli acknowledge per ogni singolo utente; utenti con errori
     * non causano il fallimento dell'intero batch.
     *
     * @param users la lista degli utenti da sincronizzare
     * @param apiKey la chiave API interna per l'autenticazione (opzionale,
     *               validata dal filtro)
     * @return una {@link ResponseEntity} con la lista di {@link UserSyncAckDto}
     */
    @PutMapping("/sync")
    public ResponseEntity<List<UserSyncAckDto>> syncUsers(
            @RequestBody List<UserSyncDto> users,
            @RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey) {
        // Whole batch returns 200 with per-user acks — poison users do NOT cause a 5xx.
        return ResponseEntity.ok(syncUsersUseCase.syncUsers(users));
    }

    /**
     * M4 — returns the number of rows currently held in the
     * {@code replicated_users} table. The body is a bare JSON number
     * (e.g. {@code 7}); the central {@code LocalServerUserCountRestAdapter}
     * deserialises it as {@link Long}.
     */
    /**
     * Restituisce il numero di righe nella tabella {@code replicated_users}.
     * Utilizzato dal sistema centrale per la riconciliazione.
     *
     * @return una {@link ResponseEntity} con il conteggio degli utenti
     */
    @GetMapping("/count")
    public ResponseEntity<Long> replicatedUsersCount() {
        return ResponseEntity.ok(userRepository.count());
    }
}