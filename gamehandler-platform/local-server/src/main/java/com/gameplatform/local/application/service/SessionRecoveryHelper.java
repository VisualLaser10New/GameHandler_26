package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.shared.domain.model.StopReason;
import org.springframework.stereotype.Component;

/**
 * Delegator sottile verso {@link SessionAbortHelper}. Mantenuto come
 * {@link Component} per non rompere i chiamanti esistenti
 * ({@link SessionRecoveryService}) e i relativi test. Delega a
 * {@link SessionAbortHelper#abortAndEmit} con stopReasonCode SERVER_RESTART.
 *
 * @see SessionAbortHelper
 * @see SessionRecoveryService
 */
@Component
public class SessionRecoveryHelper {

    private final SessionAbortHelper sessionAbortHelper;

    /**
     * Costruisce l'helper con il delegato per l'abort delle sessioni.
     *
     * @param sessionAbortHelper l'helper per l'abort e l'emissione eventi (non null)
     */
    public SessionRecoveryHelper(SessionAbortHelper sessionAbortHelper) {
        this.sessionAbortHelper = sessionAbortHelper;
    }

    /**
     * Abortisce una sessione con stopReasonCode SERVER_RESTART delegando
     * a {@link SessionAbortHelper#abortAndEmit}.
     *
     * @param session la sessione da abortire
     * @throws Exception in caso di errore (propaga da SessionAbortHelper)
     */
    public void abortSession(GameSession session) throws Exception {
        sessionAbortHelper.abortAndEmit(session, StopReason.ABORTED, "SERVER_RESTART");
    }
}