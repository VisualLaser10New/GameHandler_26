package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.central.domain.model.AggregatedStatistics;
import com.gameplatform.central.domain.ports.out.StatisticsRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.AggregatedStatisticsJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.mapper.StatisticsMapper;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.StatisticsJpaRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adapter JPA che implementa il port {@link StatisticsRepository} per la
 * persistenza delle statistiche aggregate su MySQL. Espone il salvataggio e la
 * ricerca per edificio, tipo di gioco e periodo, inclusa la variante con
 * lock pessimistico per gli aggiornamenti concorrenti.
 *
 * @see StatisticsRepository
 */
@Component
public class StatisticsRepositoryAdapter implements StatisticsRepository {

    private final StatisticsJpaRepository jpaRepository;
    private final StatisticsMapper mapper;

    /**
     * Costruisce l'adapter iniettando il repository JPA e il mapper delle statistiche.
     *
     * @param jpaRepository repository JPA per la gestione delle entit&agrave; di statistiche aggregate
     * @param mapper        mapper che converte tra il modello di dominio e l'entit&agrave; JPA
     */
    public StatisticsRepositoryAdapter(StatisticsJpaRepository jpaRepository, StatisticsMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    /**
     * Salva (o aggiorna) una statistica aggregata e restituisce l'entit&agrave; persistita.
     *
     * @param stats la statistica aggregata da persistere; non deve essere {@code null}
     * @return la statistica aggregata salvata, con eventuali valorizzazioni gestite dal database
     * @see StatisticsJpaRepository#saveAndFlush
     */
    @Override
    public AggregatedStatistics save(AggregatedStatistics stats) {
        // S3 / C-R1: saveAndFlush forces the INSERT/UPDATE to be sent to the DB at the
        // call site (inside the application service's reachable try-catch) so the unique
        // constraint uk_building_type_period is enforced here rather than at tx commit.
        // This makes the first-bucket insert race catchable by SyncEventProcessor, which
        // then retries via the pessimistic-lock merge path.
        AggregatedStatisticsJpaEntity entity = mapper.toEntity(stats);
        AggregatedStatisticsJpaEntity savedEntity = jpaRepository.saveAndFlush(entity);
        return mapper.toDomain(savedEntity);
    }

    /**
     * Restituisce la statistica aggregata per edificio, tipo di gioco e periodo.
     *
     * @param buildingId   l'identificativo dell'edificio; se {@code null} restituisce {@link Optional#empty()}
     * @param gameType     il tipo di gioco; se {@code null} restituisce {@link Optional#empty()}
     * @param periodStart  la data di inizio periodo; se {@code null} restituisce {@link Optional#empty()}
     * @return l'{@link Optional} contenente la statistica trovata, o vuoto se assente o se un argomento &egrave; {@code null}
     * @see StatisticsJpaRepository#findByBuildingIdAndGameTypeAndPeriodStart
     */
    @Override
    public Optional<AggregatedStatistics> findByBuildingAndTypeAndPeriod(BuildingId buildingId, GameType gameType, LocalDate periodStart) {
        if (buildingId == null || gameType == null || periodStart == null) {
            return Optional.empty();
        }
        return jpaRepository.findByBuildingIdAndGameTypeAndPeriodStart(buildingId.id(), gameType.name(), periodStart)
                .map(mapper::toDomain);
    }

    /**
     * Restituisce la statistica aggregata per edificio, tipo di gioco e periodo acquisendone il lock pessimistico in scrittura.
     *
     * @param buildingId   l'identificativo dell'edificio; se {@code null} restituisce {@link Optional#empty()}
     * @param gameType     il tipo di gioco; se {@code null} restituisce {@link Optional#empty()}
     * @param periodStart  la data di inizio periodo; se {@code null} restituisce {@link Optional#empty()}
     * @return l'{@link Optional} contenente la statistica trovata (bloccata), o vuoto se assente o se un argomento &egrave; {@code null}
     * @see StatisticsJpaRepository#findByBuildingIdAndGameTypeAndPeriodStartWithLock
     */
    @Override
    public Optional<AggregatedStatistics> findByBuildingAndTypeAndPeriodWithLock(BuildingId buildingId, GameType gameType, LocalDate periodStart) {
        if (buildingId == null || gameType == null || periodStart == null) {
            return Optional.empty();
        }
        return jpaRepository.findByBuildingIdAndGameTypeAndPeriodStartWithLock(buildingId.id(), gameType.name(), periodStart)
                .map(mapper::toDomain);
    }

    /**
     * Restituisce le statistiche aggregate filtrate per edificio, tipo di gioco e intervallo di periodo.
     *
     * @param buildingId l'identificativo dell'edificio; se {@code null} il filtro non &egrave; applicato
     * @param gameType   il tipo di gioco; se {@code null} il filtro non &egrave; applicato
     * @param start      la data di inizio intervallo; non deve essere {@code null}
     * @param end        la data di fine intervallo; non deve essere {@code null}
     * @return la lista delle statistiche trovate; lista vuota se non ve ne sono
     * @see StatisticsJpaRepository#findByCriteria
     */
    @Override
    public List<AggregatedStatistics> findByPeriod(BuildingId buildingId, GameType gameType, LocalDate start, LocalDate end) {
        String buildingIdStr = buildingId != null ? buildingId.id() : null;
        String gameTypeStr = gameType != null ? gameType.name() : null;
        return jpaRepository.findByCriteria(buildingIdStr, gameTypeStr, start, end).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }
}
