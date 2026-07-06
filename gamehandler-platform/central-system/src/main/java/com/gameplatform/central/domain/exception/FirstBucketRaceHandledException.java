package com.gameplatform.central.domain.exception;

import com.gameplatform.central.application.service.StatisticsFirstBucketRaceRetryHelper;
import com.gameplatform.central.application.service.SyncEventProcessor;

/**
 * Sentinel thrown by {@code update*Stats} after a first-bucket insert race has been
 * resolved in a fresh REQUIRES_NEW tx by {@link StatisticsFirstBucketRaceRetryHelper}.
 *
 * <p>{@link SyncEventProcessor#processOne} catches this separately from
 * {@code DataIntegrityViolationException} and returns {@code true} WITHOUT
 * re-saving processed_events (the helper's tx already committed the dedup record).</p>
 */
public class FirstBucketRaceHandledException extends RuntimeException {
    public FirstBucketRaceHandledException(String message) {
        super(message);
    }
}
