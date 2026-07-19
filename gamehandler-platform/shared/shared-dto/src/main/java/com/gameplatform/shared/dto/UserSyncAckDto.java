package com.gameplatform.shared.dto;

/**
 * Record che rappresenta la conferma per singolo utente restituita dal
 * local-server sull'endpoint {@code PUT /internal/users/sync} (contratto M3).
 *
 * <p>Viene restituita una conferma per ciascun {@link UserSyncDto} in ingresso,
 * mantenendo l'ordine di input:
 * <ul>
 *   <li>{@code applied=true, reason=null} → salvataggio eseguito con successo.</li>
 *   <li>{@code applied=true, reason="STALE_EVENT"} → evento obsoleto ignorato
 *       deliberatamente (considerato comunque un successo ai fini dell'avanzamento).</li>
 *   <li>{@code applied=false, reason="VALIDATION_ERROR: &lt;msg&gt;"} → utente
 *       non valido (es. username vuoto); l'evento viene marcato come FAILED lato
 *       central-server e non viene registrato alcun avanzamento, ma il batch
 *       NON viene interrotto.</li>
 * </ul>
 *
 * @param userId identificativo dell'utente a cui si riferisce la conferma.
 * @param applied {@code true} se l'evento è stato applicato o ignorato volontariamente,
 *                {@code false} in caso di errore di validazione.
 * @param reason motivo dell'esito; {@code null} in caso di successo, oppure una
 *               costante come {@code "STALE_EVENT"} o {@code "VALIDATION_ERROR: <msg>"}.
 *
 * @see UserSyncDto
 * @see UserSyncResponseDto
 */
public record UserSyncAckDto(String userId, boolean applied, String reason) {
}
