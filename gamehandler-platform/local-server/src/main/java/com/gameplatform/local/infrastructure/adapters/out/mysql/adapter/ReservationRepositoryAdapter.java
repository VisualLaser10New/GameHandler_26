package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.model.Reservation;
import com.gameplatform.local.domain.ports.out.ReservationRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.ReservationJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.ReservationMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.ReservationJpaRepository;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.ReservationId;
import com.gameplatform.shared.domain.model.ReservationStatus;
import com.gameplatform.shared.domain.model.UserId;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adapter JPA per il port {@link ReservationRepository}.
 * Gestisce la persistenza delle prenotazioni delle postazioni di gioco,
 * con gestione dei conflitti di concorrenza tramite {@link OptimisticLockingFailureException}
 * convertita in {@link ConcurrentStateException}.
 *
 * @see ReservationRepository
 * @see ReservationJpaRepository
 * @see com.gameplatform.local.domain.exception.ConcurrentStateException
 */
@Component
public class ReservationRepositoryAdapter implements ReservationRepository {

    private final ReservationJpaRepository jpaRepository;
    private final ReservationMapper mapper;

    /**
     * Costruisce un nuovo adapter con le dipendenze necessarie.
     *
     * @param jpaRepository repository JPA per le prenotazioni
     * @param mapper        mapper per la conversione tra entity e dominio
     */
    public ReservationRepositoryAdapter(ReservationJpaRepository jpaRepository, ReservationMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    /**
     * Salva una prenotazione nel database con flush immediato.
     * In caso di conflitto di versione, lancia una {@link ConcurrentStateException}.
     *
     * @param reservation la prenotazione da salvare
     * @return la prenotazione persistita
     * @throws com.gameplatform.local.domain.exception.ConcurrentStateException in caso di modifica concorrente
     */
    @Override
    public Reservation save(Reservation reservation) {
        ReservationJpaEntity entity = mapper.toEntity(reservation);
        try {
            ReservationJpaEntity saved = jpaRepository.saveAndFlush(entity);
            return mapper.toDomain(saved);
        } catch (org.springframework.dao.OptimisticLockingFailureException ex) {
            throw new com.gameplatform.local.domain.exception.ConcurrentStateException(
                "Concurrent modification of reservation " + reservation.getId().value(), ex);
        }
    }

    /**
     * Recupera una prenotazione tramite il suo identificativo.
     *
     * @param id l'identificativo della prenotazione
     * @return un {@code Optional} contenente la prenotazione, vuoto se non trovata
     */
    @Override
    public Optional<Reservation> findById(ReservationId id) {
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    /**
     * Recupera tutte le prenotazioni effettuate da un dato utente.
     *
     * @param userId l'identificativo dell'utente
     * @return una lista di prenotazioni per l'utente specificato
     */
    @Override
    public List<Reservation> findByUserId(UserId userId) {
        return jpaRepository.findByUserId(userId.value()).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    /**
     * Recupera tutte le prenotazioni per un dato gioco.
     *
     * @param gameId l'identificativo del gioco
     * @return una lista di prenotazioni per il gioco specificato
     */
    @Override
    public List<Reservation> findByGameId(GameId gameId) {
        return jpaRepository.findByGameId(gameId.id()).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    /**
     * Recupera tutte le prenotazioni con un dato stato.
     *
     * @param status lo stato delle prenotazioni da filtrare
     * @return una lista di prenotazioni con lo stato specificato
     */
    @Override
    public List<Reservation> findByStatus(ReservationStatus status) {
        return jpaRepository.findByStatus(status.name()).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    /**
     * Recupera le prenotazioni scadute in stato PENDING o CONFIRMED,
     * con data di fine precedente all'istante specificato.
     *
     * @param now l'istante di riferimento per determinare la scadenza
     * @return una lista di prenotazioni scadute
     */
    @Override
    public List<Reservation> findExpired(Instant now) {
        return jpaRepository.findByStatusInAndEndTimeBefore(List.of(ReservationStatus.PENDING.name(), ReservationStatus.CONFIRMED.name()), now).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    /**
     * Conta il numero di prenotazioni associate a una lista di giochi.
     *
     * @param gameIds la lista degli identificativi dei giochi
     * @return il conteggio delle prenotazioni, 0 se la lista è nulla o vuota
     */
    @Override
    public int countByGameIds(List<GameId> gameIds) {
        if (gameIds == null || gameIds.isEmpty()) {
            return 0;
        }
        List<String> ids = gameIds.stream()
            .map(GameId::id)
            .collect(Collectors.toList());
        return jpaRepository.countByGameIdIn(ids);
    }
}
