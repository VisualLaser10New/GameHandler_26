package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.model.AdminRequestLocal;
import com.gameplatform.local.domain.model.AdminRequestStatus;
import com.gameplatform.local.domain.ports.out.AdminRequestRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.AdminRequestLocalJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.AdminRequestLocalMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.AdminRequestLocalJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adapter JPA per il port {@link AdminRequestRepository}.
 * Gestisce la persistenza delle richieste amministrative locali,
 * inclusi i metodi {@code markCompleted} e {@code markFailed} che
 * eseguono aggiornamenti bulk condizionali sulla tabella delle
 * richieste amministrative, garantendo idempotenza in caso di
 * riconsegna dello stesso evento di ritorno.
 *
 * @see AdminRequestRepository
 * @see AdminRequestLocalJpaRepository
 */
@Component
public class AdminRequestRepositoryAdapter implements AdminRequestRepository {

    private final AdminRequestLocalJpaRepository jpaRepository;
    private final AdminRequestLocalMapper mapper;
    private final Clock clock;

    /**
     * Costruisce un nuovo adapter con le dipendenze necessarie.
     *
     * @param jpaRepository repository JPA per le richieste amministrative
     * @param mapper        mapper per la conversione tra entity e dominio
     * @param clock         orologio per la generazione dei timestamp
     */
    public AdminRequestRepositoryAdapter(AdminRequestLocalJpaRepository jpaRepository,
                                         AdminRequestLocalMapper mapper,
                                         Clock clock) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Salva una richiesta amministrativa nel database.
     *
     * @param request la richiesta amministrativa da salvare, può essere {@code null}
     * @return la richiesta amministrativa persistita, {@code null} se l'argomento è {@code null}
     */
    @Override
    @Transactional
    public AdminRequestLocal save(AdminRequestLocal request) {
        if (request == null) {
            return null;
        }
        AdminRequestLocalJpaEntity entity = mapper.toEntity(request);
        AdminRequestLocalJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    /**
     * Recupera una richiesta amministrativa tramite il suo identificativo.
     *
     * @param requestId l'identificativo della richiesta
     * @return un {@code Optional} contenente la richiesta amministrativa, vuoto se non trovata o se l'identificativo è nullo/vuoto
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<AdminRequestLocal> findByRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return Optional.empty();
        }
        return jpaRepository.findById(requestId).map(mapper::toDomain);
    }

    /**
     * Recupera tutte le richieste amministrative effettuate da un dato utente.
     *
     * @param actingUserId l'identificativo dell'utente che ha effettuato le richieste
     * @return una lista di richieste amministrative, vuota se l'identificativo è nullo/vuoto o se non ci sono risultati
     */
    @Override
    @Transactional(readOnly = true)
    public List<AdminRequestLocal> findByActingUserId(String actingUserId) {
        if (actingUserId == null || actingUserId.isBlank()) {
            return List.of();
        }
        return jpaRepository.findByActingUserId(actingUserId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * Recupera le richieste amministrative filtrate per utente e stato.
     *
     * @param actingUserId l'identificativo dell'utente
     * @param status       lo stato delle richieste da filtrare
     * @return una lista di richieste amministrative corrispondenti ai criteri, vuota se i parametri sono nulli/vuoti
     */
    @Override
    @Transactional(readOnly = true)
    public List<AdminRequestLocal> findByActingUserIdAndStatus(String actingUserId, String status) {
        if (actingUserId == null || actingUserId.isBlank() || status == null || status.isBlank()) {
            return List.of();
        }
        return jpaRepository.findByActingUserIdAndStatus(actingUserId, status).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * Marca una richiesta amministrativa come completata con il risultato fornito.
     * L'operazione è idempotente: una seconda chiamata su una riga già risolta
     * non produce effetti.
     *
     * @param requestId  l'identificativo della richiesta da completare
     * @param resultData i dati del risultato da associare
     * @param now        il timestamp corrente, se {@code null} viene utilizzato l'orologio del sistema
     * @return il numero di righe modificate
     */
    @Override
    @Transactional
    public int markCompleted(String requestId, String resultData, Instant now) {
        if (requestId == null || requestId.isBlank()) {
            return 0;
        }
        Instant ts = now != null ? now : Instant.now(clock);
        return jpaRepository.markCompleted(requestId, resultData, ts);
    }

    /**
     * Marca una richiesta amministrativa come fallita con il motivo fornito.
     * L'operazione è idempotente: una seconda chiamata su una riga già risolta
     * non produce effetti.
     *
     * @param requestId l'identificativo della richiesta da marcare come fallita
     * @param reason    il motivo del fallimento
     * @param now       il timestamp corrente, se {@code null} viene utilizzato l'orologio del sistema
     * @return il numero di righe modificate
     */
    @Override
    @Transactional
    public int markFailed(String requestId, String reason, Instant now) {
        if (requestId == null || requestId.isBlank()) {
            return 0;
        }
        Instant ts = now != null ? now : Instant.now(clock);
        return jpaRepository.markFailed(requestId, reason, ts);
    }

    /**
     * Recupera le richieste amministrative in stato PENDING più vecchie della soglia specificata.
     *
     * @param threshold la soglia temporale; le richieste create prima di questo istante vengono considerate
     * @return una lista di richieste in attesa più vecchie della soglia, vuota se la soglia è {@code null}
     */
    @Override
    @Transactional(readOnly = true)
    public List<AdminRequestLocal> findPendingOlderThan(Instant threshold) {
        if (threshold == null) {
            return List.of();
        }
        return jpaRepository.findByStatusAndCreatedAtBefore(AdminRequestStatus.PENDING.name(), threshold).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }
}