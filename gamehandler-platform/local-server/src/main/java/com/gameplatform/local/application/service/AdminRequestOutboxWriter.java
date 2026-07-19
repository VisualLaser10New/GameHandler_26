package com.gameplatform.local.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gameplatform.local.domain.model.AdminRequestLocal;
import com.gameplatform.local.domain.model.AdminRequestStatus;
import com.gameplatform.local.domain.model.OutboxEvent;
import com.gameplatform.local.domain.model.OutboxEventStatus;
import com.gameplatform.local.domain.ports.out.AdminRequestRepository;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.shared.dto.AdminRequestDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Helper component che incapsula la scrittura atomica di una riga
 * {@code admin_requests_local} in stato PENDING insieme al corrispondente
 * evento outbox {@code *_REQUESTED}. Il singolo UUID {@code requestId}
 * funge sia da chiave primaria per {@code admin_requests_local} sia da
 * {@code eventId} per l'outbox, consentendo ai servizi {@code *SyncService}
 * di chiamare {@code markCompleted(requestId)} quando l'evento di ritorno
 * dal Central arriva con {@code originatingRequestId}.
 *
 * <p>Una richiesta admin {@code FAILED} puo' anche essere persistita senza
 * riga outbox (usata dal pre-controllo DRAFT W12e/W12f). L'intera scrittura
 * e' avvolta dal confine {@code @Transactional} del chiamante cosicche' le
 * righe {@code AdminRequestLocal} e {@code OutboxEvent} siano persistite
 * atomicamente.</p>
 *
 * @see AdminRequestLocal
 * @see OutboxEvent
 * @see AdminRequestRepository
 * @see OutboxEventRepository
 */
@Component
public class AdminRequestOutboxWriter {

    private static final Logger log = LoggerFactory.getLogger(AdminRequestOutboxWriter.class);

    private final AdminRequestRepository adminRequestRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Costruisce il writer con le dipendenze necessarie per la persistenza
     * atomica delle richieste admin e degli eventi outbox.
     *
     * @param adminRequestRepository il repository per le richieste admin locali
     * @param outboxEventRepository  il repository per gli eventi outbox
     * @param objectMapper           il mapper JSON per serializzare i payload
     * @param clock                  l'orologio per la generazione dei timestamp
     */
    public AdminRequestOutboxWriter(AdminRequestRepository adminRequestRepository,
                                     OutboxEventRepository outboxEventRepository,
                                     ObjectMapper objectMapper,
                                     Clock clock) {
        this.adminRequestRepository = adminRequestRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Persiste atomicamente un {@link AdminRequestLocal} in stato PENDING e il
     * corrispondente {@link OutboxEvent} in stato PENDING contenente il payload
     * serializzato. Lo stesso UUID viene utilizzato per {@code requestId} e
     * {@code eventId}.
     *
     * @param eventType    il tipo di evento (es. ROLE_ASSIGNMENT_REQUESTED)
     * @param actingUserId l'identificativo dell'utente che ha richiesto l'operazione
     * @param actingRole   il ruolo con cui l'utente agisce
     * @param buildingId   l'identificativo del building di competenza
     * @param payload      l'oggetto da serializzare come payload JSON dell'evento
     * @return il DTO della richiesta admin appena creata
     */
    public AdminRequestDto writePendingRequest(String eventType,
                                                 String actingUserId,
                                                 String actingRole,
                                                 String buildingId,
                                                 Object payload) {
        String requestId = UUID.randomUUID().toString();
        Instant now = Instant.now(clock);
        String payloadJson = serialize(eventType, payload, requestId);
        AdminRequestLocal adminReq = new AdminRequestLocal(
                requestId, eventType, actingUserId, actingRole, buildingId,
                payloadJson, AdminRequestStatus.PENDING.name(), null, now, null, requestId);
        adminRequestRepository.save(adminReq);
        OutboxEvent outbox = new OutboxEvent(
                requestId, eventType, payloadJson, OutboxEventStatus.PENDING.name(), now, null, 0);
        outboxEventRepository.save(outbox);
        log.info("Admin request {} persisted as PENDING (eventType={}, user={}, building={})",
                requestId, eventType, actingUserId, buildingId);
        return toDto(adminReq);
    }

    /**
     * Persiste un {@link AdminRequestLocal} in stato FAILED SENZA scrivere la
     * corrispondente riga outbox. Utilizzato dal pre-controllo DRAFT W12e/W12f
     * per rifiutare immediatamente con FAILED quando il torneo non e' in stato
     * DRAFT. Il parametro {@code reason} viene memorizzato come JSON in
     * {@code result_data}.
     *
     * @param eventType    il tipo di evento
     * @param actingUserId l'identificativo dell'utente richiedente
     * @param actingRole   il ruolo con cui l'utente agisce
     * @param buildingId   l'identificativo del building di competenza
     * @param payload      l'oggetto da serializzare come payload JSON
     * @param reason       la stringa JSON contenente il motivo del fallimento
     * @return il DTO della richiesta admin in stato FAILED
     */
    public AdminRequestDto writeFailedRequest(String eventType,
                                                String actingUserId,
                                                String actingRole,
                                                String buildingId,
                                                Object payload,
                                                String reason) {
        String requestId = UUID.randomUUID().toString();
        Instant now = Instant.now(clock);
        String payloadJson = serialize(eventType, payload);
        AdminRequestLocal adminReq = new AdminRequestLocal(
                requestId, eventType, actingUserId, actingRole, buildingId,
                payloadJson, AdminRequestStatus.FAILED.name(), reason, now, now, null);
        adminRequestRepository.save(adminReq);
        log.warn("Admin request {} persisted as FAILED (eventType={}, user={}, reason={})",
                requestId, eventType, actingUserId, reason);
        return toDto(adminReq);
    }

    /**
     * Serializza il payload in JSON delegando al metodo a tre parametri
     * con requestId nullo.
     *
     * @param eventType il tipo di evento (non null)
     * @param payload   l'oggetto da serializzare (non null)
     * @return la stringa JSON del payload serializzato
     * @throws IllegalArgumentException se la serializzazione fallisce
     */
    private String serialize(String eventType, Object payload) {
        return serialize(eventType, payload, null);
    }

    /**
     * Serializza il payload in JSON e, se requestId non e' null e il tree
     * JSON risultante e' un oggetto, injecta i campi {@code eventId} e
     * {@code requestId} quando questi sono presenti ma null.
     *
     * @param eventType il tipo di evento (non null)
     * @param payload   l'oggetto da serializzare (non null)
     * @param requestId l'identificativo opzionale da injectare nel JSON; puo' essere null
     * @return la stringa JSON del payload serializzato
     * @throws IllegalArgumentException se la serializzazione fallisce
     */
    private String serialize(String eventType, Object payload, String requestId) {
        try {
            JsonNode tree = objectMapper.valueToTree(payload);
            if (requestId != null && tree != null && tree.isObject()) {
                ObjectNode obj = (ObjectNode) tree;
                if (obj.has("eventId") && obj.get("eventId").isNull()) {
                    obj.put("eventId", requestId);
                }
                if (obj.has("requestId") && obj.get("requestId").isNull()) {
                    obj.put("requestId", requestId);
                }
            }
            return objectMapper.writeValueAsString(tree);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Failed to serialize outbox payload for event " + eventType, e);
        }
    }

    /**
     * Converte un {@link AdminRequestLocal} nel corrispondente
     * {@link AdminRequestDto}.
     *
     * @param adminReq l'entita' del modello di dominio (non null)
     * @return il DTO con tutti i campi mappati uno-a-uno
     */
    static AdminRequestDto toDto(AdminRequestLocal adminReq) {
        return new AdminRequestDto(
                adminReq.getRequestId(),
                adminReq.getEventType(),
                adminReq.getActingUserId(),
                adminReq.getActingRole(),
                adminReq.getBuildingId(),
                adminReq.getPayloadJson(),
                adminReq.getStatus(),
                adminReq.getResultDataJson(),
                adminReq.getCreatedAt(),
                adminReq.getCompletedAt(),
                adminReq.getOutboxEventId()
        );
    }
}