package com.gameplatform.shared.dto;

import java.time.Instant;

/**
 * Record che rappresenta il payload dell'outbox e l'elemento di batch di
 * sincronizzazione per il flusso di replica dei metadati tra LOCAL_ADMIN e
 * building (Central → Local).
 *
 * <p>Tipi di evento supportati:
 * <ul>
 *   <li>{@code LOCAL_ADMIN_BUILDING_ASSIGNED} — crea o aggiorna il binding sul
 *       nodo locale; {@code assignedAt} contiene il timestamp di assegnazione.</li>
 *   <li>{@code LOCAL_ADMIN_BUILDING_REVOKED} — rimuove il binding sul nodo
 *       locale; {@code assignedAt} è {@code null} e viene ignorato.</li>
 * </ul>
 *
 * <p>Il campo {@code eventId} è l'identificativo (UUID) dell'evento outbox e
 * consente al nodo locale di eliminare i duplicati e al nodo centrale di
 * tracciare l'avanzamento della replica.</p>
 *
 * @param eventId    identificativo dell'evento outbox (UUID); non è {@code null}
 * @param eventType  tipo di evento, uno tra {@code LOCAL_ADMIN_BUILDING_ASSIGNED}
 *                   e {@code LOCAL_ADMIN_BUILDING_REVOKED}; non è {@code null}
 * @param userId     identificativo dell'utente LOCAL_ADMIN; non è {@code null}
 * @param buildingId identificativo del building; non è {@code null}
 * @param assignedAt timestamp di assegnazione; è {@code null} per gli eventi di
 *                   revoca e viene ignorato in tal caso
 *
 * @see com.gameplatform.shared.dto.LocalAdminDto
 */
public record LocalAdminBuildingEventDto(
        String eventId,
        String eventType,
        String userId,
        String buildingId,
        Instant assignedAt
) {
}