package com.gameplatform.central.application.service;

import com.gameplatform.central.domain.ports.in.ReceiveSyncDataUseCase;
import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import com.gameplatform.central.domain.ports.out.ProcessedEventRepository;
import com.gameplatform.central.domain.ports.out.StatisticsRepository;
import com.gameplatform.central.domain.ports.in.RegisterUserFromSyncUseCase;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.dto.OutboxEventDto;
import com.gameplatform.shared.dto.SyncPayloadDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Application service that receives and processes sync payloads from local servers.
 *
 * <p>Per-event transaction isolation (fix for BUG-SYNC-01 / C-01): each event is
 * processed by {@link SyncEventProcessor#processOne} in its own
 * {@code REQUIRES_NEW} transaction, so a poison event aborts only its own tx,
 * not the whole batch. This method no longer holds an outer transaction — the
 * heartbeat update runs after the loop regardless of individual event outcomes.</p>
 */
@Service
public class SyncReceiverService implements ReceiveSyncDataUseCase {

    private static final Logger log = LoggerFactory.getLogger(SyncReceiverService.class);

    private final SyncEventProcessor syncEventProcessor;
    private final LocalServerRegistryPort localServerRegistryPort;
    private final Clock clock;

    /**
     * Production constructor — Spring injects the {@link SyncEventProcessor} bean,
     * which is a transactional proxy so {@code @Transactional(REQUIRES_NEW)} on
     * {@link SyncEventProcessor#processOne} takes effect.
     */
    @org.springframework.beans.factory.annotation.Autowired
    public SyncReceiverService(SyncEventProcessor syncEventProcessor,
                               LocalServerRegistryPort localServerRegistryPort,
                               Clock clock) {
        this.syncEventProcessor = syncEventProcessor;
        this.localServerRegistryPort = localServerRegistryPort;
        this.clock = clock;
    }

    /**
     * Backward-compat constructor for existing unit tests. Builds a plain
     * (non-proxied) {@link SyncEventProcessor} so {@code @Transactional} is inert
     * — sufficient for unit-level poison-isolation assertions (loop continues on
     * exception) without a Spring context.
     */
    public SyncReceiverService(ProcessedEventRepository processedEventRepository,
                               StatisticsRepository statisticsRepository,
                               LocalServerRegistryPort localServerRegistryPort,
                               RegisterUserFromSyncUseCase registerUserFromSyncUseCase,
                               ObjectMapper objectMapper) {
        this(new SyncEventProcessor(processedEventRepository, statisticsRepository,
                        registerUserFromSyncUseCase, objectMapper, Clock.systemUTC()),
                localServerRegistryPort, Clock.systemUTC());
    }

    /**
     * Backward-compat constructor with explicit {@link Clock}.
     */
    public SyncReceiverService(ProcessedEventRepository processedEventRepository,
                               StatisticsRepository statisticsRepository,
                               LocalServerRegistryPort localServerRegistryPort,
                               RegisterUserFromSyncUseCase registerUserFromSyncUseCase,
                               ObjectMapper objectMapper,
                               Clock clock) {
        this(new SyncEventProcessor(processedEventRepository, statisticsRepository,
                        registerUserFromSyncUseCase, objectMapper, clock),
                localServerRegistryPort, clock);
    }

    @Override
    public void receiveSyncPayload(SyncPayloadDto payload) {
        if (payload == null || payload.events() == null || payload.events().isEmpty()) {
            return;
        }
        BuildingId buildingId = new BuildingId(payload.buildingId());

        for (OutboxEventDto event : payload.events()) {
            try {
                syncEventProcessor.processOne(buildingId, event);
            } catch (Exception e) {
                log.error("Failed to process sync event [{}] type=[{}] from building [{}]: {}. Marking as processed (poison isolation).",
                        event.eventId(), event.eventType(), buildingId, e.getMessage(), e);
                try {
                    syncEventProcessor.markProcessed(event.eventId());
                } catch (Exception markEx) {
                    log.error("Failed to mark failed event [{}] as processed: {}", event.eventId(), markEx.getMessage(), markEx);
                }
            }
        }

        // Heartbeat: update lastSeenAt for this building's server after a successful sync
        localServerRegistryPort.updateLastSeenAt(buildingId, Instant.now(clock));
    }
}
