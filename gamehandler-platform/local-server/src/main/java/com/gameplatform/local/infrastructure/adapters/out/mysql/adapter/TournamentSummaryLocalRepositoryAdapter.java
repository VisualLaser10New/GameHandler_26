package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.model.TournamentSummaryLocal;
import com.gameplatform.local.domain.ports.out.TournamentSummaryLocalRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TournamentSummaryLocalJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.TournamentSummaryLocalMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.TournamentSummaryLocalJpaRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adapter JPA per il port {@link TournamentSummaryLocalRepository}.
 * Gestisce la persistenza dei riepiloghi dei tornei, con operazioni
 * di upsert per chiave primaria {@code tournament_id} che garantiscono
 * idempotenza in caso di riapplicazione dello stesso snapshot di riepilogo.
 *
 * @see TournamentSummaryLocalRepository
 * @see TournamentSummaryLocalJpaRepository
 */
@Component
public class TournamentSummaryLocalRepositoryAdapter implements TournamentSummaryLocalRepository {

    private final TournamentSummaryLocalJpaRepository jpaRepository;
    private final TournamentSummaryLocalMapper mapper;

    /**
     * Costruisce un nuovo adapter con le dipendenze necessarie.
     *
     * @param jpaRepository repository JPA per i riepiloghi dei tornei
     * @param mapper        mapper per la conversione tra entity e dominio
     */
    public TournamentSummaryLocalRepositoryAdapter(TournamentSummaryLocalJpaRepository jpaRepository,
                                                   TournamentSummaryLocalMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    /**
     * Salva un riepilogo di torneo nel database (upsert per chiave primaria).
     *
     * @param summary il riepilogo del torneo da salvare
     * @return il riepilogo persistito, {@code null} se l'argomento è {@code null}
     */
    @Override
    @Transactional
    public TournamentSummaryLocal save(TournamentSummaryLocal summary) {
        if (summary == null) {
            return null;
        }
        TournamentSummaryLocalJpaEntity entity = mapper.toEntity(summary);
        TournamentSummaryLocalJpaEntity savedEntity = jpaRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    /**
     * Recupera un riepilogo di torneo tramite l'identificativo del torneo.
     *
     * @param tournamentId l'identificativo del torneo
     * @return un {@code Optional} contenente il riepilogo, vuoto se non trovato o se l'identificativo è {@code null}
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TournamentSummaryLocal> findById(TournamentId tournamentId) {
        if (tournamentId == null) {
            return Optional.empty();
        }
        return jpaRepository.findById(tournamentId.value()).map(mapper::toDomain);
    }

    /**
     * Recupera tutti i riepiloghi dei tornei.
     *
     * @return una lista completa di tutti i riepiloghi dei tornei
     */
    @Override
    @Transactional(readOnly = true)
    public List<TournamentSummaryLocal> findAll() {
        return jpaRepository.findAll().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * Elimina un riepilogo di torneo tramite l'identificativo del torneo.
     *
     * @param tournamentId l'identificativo del torneo da eliminare; se {@code null} l'operazione non viene eseguita
     */
    @Override
    @Transactional
    public void deleteById(TournamentId tournamentId) {
        if (tournamentId == null) {
            return;
        }
        jpaRepository.deleteById(tournamentId.value());
    }

    /**
     * Verifica se esiste un riepilogo per un dato torneo.
     *
     * @param tournamentId l'identificativo del torneo
     * @return {@code true} se il riepilogo esiste, {@code false} altrimenti o se l'identificativo è {@code null}
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsById(TournamentId tournamentId) {
        if (tournamentId == null) {
            return false;
        }
        return jpaRepository.existsById(tournamentId.value());
    }
}
