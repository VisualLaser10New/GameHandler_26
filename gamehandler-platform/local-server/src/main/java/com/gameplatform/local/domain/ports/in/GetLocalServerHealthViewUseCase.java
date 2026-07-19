package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.dto.ServerHealthViewDto;

/**
 * Use case per la consultazione dello stato di salute del server locale.
 * Aggrega il conteggio dei messaggi in outbox in sospeso del nodo locale
 * con il registro di tutti i server locali registrati noti.
 *
 * @see com.gameplatform.shared.dto.ServerHealthViewDto
 */
public interface GetLocalServerHealthViewUseCase {

    /**
     * Restituisce la vista aggregata dello stato di salute del server.
     *
     * @return il DTO con la vista dello stato di salute del server
     */
    ServerHealthViewDto getHealthView();
}