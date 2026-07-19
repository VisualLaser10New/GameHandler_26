package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.central.domain.model.ProcessedEvent;
import com.gameplatform.central.domain.ports.out.ProcessedEventRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.ProcessedEventJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.ProcessedEventJpaRepository;
import org.springframework.stereotype.Component;

/**
 * Adapter JPA che implementa il port {@link ProcessedEventRepository} per la
 * persistenza degli eventi gi&agrave; processati su MySQL. Garantisce l'idempotenza
 * dell'elaborazione tramite tracciamento degli identificativi elaborati.
 *
 * @see ProcessedEventRepository
 */
@Component
public class ProcessedEventRepositoryAdapter implements ProcessedEventRepository {

    private final ProcessedEventJpaRepository jpaRepository;

    /**
     * Costruisce l'adapter iniettando il repository JPA dedicato agli eventi processati.
     *
     * @param jpaRepository repository JPA per la gestione delle entit&agrave; di evento processato
     */
    public ProcessedEventRepositoryAdapter(ProcessedEventJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    /**
     * Verifica se l'evento identificato risulta gi&agrave; stato processato.
     *
     * @param eventId l'identificativo dell'evento da verificare; se {@code null} restituisce {@code false}
     * @return {@code true} se l'evento &egrave; gi&agrave; stato processato, {@code false} altrimenti
     * @see ProcessedEventJpaRepository#existsById
     */
    @Override
    public boolean existsByEventId(String eventId) {
        if (eventId == null) {
            return false;
        }
        return jpaRepository.existsById(eventId);
    }

    /**
     * Registra un evento come processato persistendone l'identificativo.
     *
     * @param event l'evento processato da salvare; se {@code null} il metodo non effettua alcuna operazione
     * @see ProcessedEventJpaRepository#save
     */
    @Override
    public void save(ProcessedEvent event) {
        if (event == null) {
            return;
        }
        ProcessedEventJpaEntity entity = new ProcessedEventJpaEntity(
                event.getEventId(),
                event.getProcessedAt()
        );
        jpaRepository.save(entity);
    }
}
