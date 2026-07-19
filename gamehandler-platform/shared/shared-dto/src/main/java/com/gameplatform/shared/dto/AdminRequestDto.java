package com.gameplatform.shared.dto;

import java.time.Instant;

/**
 * Read-model projection of a Local admin-request row for the
 * {@code GET /api/admin/requests[?requestId=]} Local endpoint
 * (PIANO §7.B). Sourced from the {@code admin_requests_local} table;
 * the {@code payload} and {@code resultData} columns are returned as
 * opaque {@link String} values (the JSON-encoded payload as written by
 * the W use case that created the request) so the client can interpret
 * them without depending on the request-specific DTO contract.
 *
 * Proiezione in sola lettura di una riga di richiesta admin locale, utilizzata
 * per l'endpoint Local {@code GET /api/admin/requests[?requestId=]} (PIANO §7.B).
 * I dati provengono dalla tabella {@code admin_requests_local}; le colonne
 * {@code payload} e {@code resultData} sono restituite come {@link String} opache
 * (JSON codificato così come scritto dal caso d'uso W che ha creato la richiesta),
 * così il client può interpretarle senza dipendere dal contratto DTO specifico.
 *
 * @param requestId     l'identificativo della richiesta admin; non è {@code null} e corrisponde all'{@code outboxEventId}
 * @param eventType     il tipo di evento {@code *_REQUESTED} emesso; non è {@code null}
 * @param actingUserId  l'identificativo dell'utente admin/PLAYER che ha aperto la richiesta; non è {@code null}
 * @param actingRole    il ruolo dell'utente che ha effettuato l'azione; non è {@code null}
 * @param buildingId    l'identificativo dell'edificio a cui l'utente è connesso; non è {@code null}
 * @param payload       il payload della richiesta in formato JSON; non è {@code null} (stringa vuota se assente)
 * @param status        lo stato della richiesta (PENDING / COMPLETED / FAILED); non è {@code null}
 * @param resultData    i dati di risultato in formato JSON; può essere {@code null} se non ancora disponibili
 * @param createdAt     l'istante di creazione della richiesta; non è {@code null}
 * @param completedAt   l'istante di completamento della richiesta; è {@code null} finché lo stato è PENDING
 * @param outboxEventId l'identificativo dell'evento outbox che trasporta la richiesta; non è {@code null} e corrisponde a {@code requestId}
 */
public record AdminRequestDto(
        String requestId,
        String eventType,
        String actingUserId,
        String actingRole,
        String buildingId,
        String payload,
        String status,
        String resultData,
        Instant createdAt,
        Instant completedAt,
        String outboxEventId
) {
}
