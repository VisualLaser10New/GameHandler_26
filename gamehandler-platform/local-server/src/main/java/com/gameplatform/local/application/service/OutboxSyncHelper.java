package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class OutboxSyncHelper {

    private final OutboxEventRepository outboxEventRepository;

    /**
     * Costruisce l'helper con il repository degli eventi outbox.
     *
     * @param outboxEventRepository il repository per l'accesso agli eventi outbox (non null)
     */
    public OutboxSyncHelper(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    /**
     * Marca una lista di eventi outbox come inviati (SENT).
     *
     * @param ids la lista degli identificativi degli eventi da marcare
     */
    public void markAsSent(List<String> ids) {
        if (ids != null && !ids.isEmpty()) {
            outboxEventRepository.markAsSentBatch(ids);
        }
    }

    /**
     * Incrementa il contatore di retry per una lista di eventi outbox.
     *
     * @param ids la lista degli identificativi degli eventi da incrementare
     */
    public void incrementRetry(List<String> ids) {
        if (ids != null && !ids.isEmpty()) {
            outboxEventRepository.incrementRetryBatch(ids);
        }
    }
}
