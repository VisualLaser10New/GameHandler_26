package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.shared.domain.model.StopReason;
import org.springframework.stereotype.Component;

/**
 * R3 (outbox atomicity) — thin delegator to {@link SessionAbortHelper}.
 *
 * <p>Previously this class owned the abort + release + outbox body itself,
 * guarded by a class-level {@code @Transactional}. With R3 the atomic unit has
 * been lifted into {@link SessionAbortHelper#abortAndEmit} which uses
 * {@link org.springframework.transaction.annotation.Propagation#REQUIRES_NEW}
 * on a separate bean (self-invocation would bypass the Spring proxy). This
 * class is kept as a {@link Component} so existing callers
 * ({@link SessionRecoveryService}) and their tests do not break — it now just
 * delegates to the helper with the SERVER_RESTART stopReasonCode that the
 * recovery path has always emitted.</p>
 */
@Component
public class SessionRecoveryHelper {

    private final SessionAbortHelper sessionAbortHelper;

    public SessionRecoveryHelper(SessionAbortHelper sessionAbortHelper) {
        this.sessionAbortHelper = sessionAbortHelper;
    }

    public void abortSession(GameSession session) throws Exception {
        sessionAbortHelper.abortAndEmit(session, StopReason.ABORTED, "SERVER_RESTART");
    }
}