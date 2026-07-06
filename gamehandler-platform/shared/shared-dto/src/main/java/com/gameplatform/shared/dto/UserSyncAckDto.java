package com.gameplatform.shared.dto;

/**
 * Per-user acknowledgement returned by the local-server
 * {@code PUT /internal/users/sync} endpoint (M3 contract).
 *
 * <p>One ack is returned per input {@link UserSyncDto}, in input order:
 * <ul>
 *   <li>{@code applied=true, reason=null} → happy-path save.</li>
 *   <li>{@code applied=true, reason="STALE_EVENT"} → deliberately skipped
 *       stale event (treated as success for progress purposes).</li>
 *   <li>{@code applied=false, reason="VALIDATION_ERROR: &lt;msg&gt;"} → poison
 *       user (e.g. blank username); the event is marked FAILED on the central
 *       side and no progress is recorded, but the batch is NOT aborted.</li>
 * </ul>
 */
public record UserSyncAckDto(String userId, boolean applied, String reason) {
}
