package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.AdminRequestLocal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Out-port for the {@code admin_requests_local} Local persistence of
 * async admin/PLAYER requests (PIANO §7.B). A new request is persisted
 * in {@link AdminRequestStatus#PENDING PENDING} status atomically with
 * the outbox row by the W use case; lifecycle is closed by the matching
 * {@code *SyncService} (the Central return-event carries the original
 * {@code outboxEventId} back as {@code originatingRequestId}) via
 * {@link #markCompleted}, or by the {@code AdminRequestTimeoutService}
 * via {@link #markFailed} when no return-event is observed within the
 * configured timeout.
 *
 * <p>{@link #markCompleted} and {@link #markFailed} are conditional
 * updates that only mutate rows currently in {@code PENDING} status —
 * idempotent on re-delivery of the same return-event (a second call is
 * a no-op because the row is already {@code COMPLETED}).</p>
 */
public interface AdminRequestRepository {

    AdminRequestLocal save(AdminRequestLocal request);

    Optional<AdminRequestLocal> findByRequestId(String requestId);

    List<AdminRequestLocal> findByActingUserId(String actingUserId);

    List<AdminRequestLocal> findByActingUserIdAndStatus(String actingUserId, String status);

    /**
     * Atomically transitions a {@code PENDING} admin-request to
     * {@code COMPLETED} and stores the result-data JSON. Idempotent on
     * re-delivery: a second call against an already-{@code COMPLETED} row
     * is a no-op (zero rows updated) because of the conditional
     * {@code WHERE status = 'PENDING'} clause.
     *
     * @return the number of rows actually mutated (0 if the row was
     *         already resolved or does not exist)
     */
    int markCompleted(String requestId, String resultData, Instant now);

    /**
     * Atomically transitions a {@code PENDING} admin-request to
     * {@code FAILED} and stores the reason JSON. Idempotent on re-delivery.
     *
     * @return the number of rows actually mutated
     */
    int markFailed(String requestId, String reason, Instant now);

    /**
     * Returns the PENDING admin-requests whose {@code createdAt} is
     * strictly before the given threshold — used by the
     * {@code AdminRequestTimeoutService} scheduler to time out stale
     * requests.
     */
    List<AdminRequestLocal> findPendingOlderThan(Instant threshold);
}