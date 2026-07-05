package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class OutboxSyncHelper {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxSyncHelper(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    public void markAsSent(List<String> ids) {
        if (ids != null && !ids.isEmpty()) {
            outboxEventRepository.markAsSentBatch(ids);
        }
    }

    public void incrementRetry(List<String> ids) {
        if (ids != null && !ids.isEmpty()) {
            outboxEventRepository.incrementRetryBatch(ids);
        }
    }
}
