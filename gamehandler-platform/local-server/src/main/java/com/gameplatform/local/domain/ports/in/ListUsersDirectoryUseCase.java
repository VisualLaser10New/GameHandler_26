package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.dto.UsersDirectoryDto;

import java.util.List;

/**
 * Use case per la lettura dell'anagrafica utenti locale. Restituisce
 * una proiezione di tutti gli utenti replicati localmente, escludendo
 * il campo della password hashata per motivi di sicurezza.
 *
 * @see com.gameplatform.shared.dto.UsersDirectoryDto
 */
public interface ListUsersDirectoryUseCase {

    /**
     * Restituisce l'elenco completo di tutti gli utenti replicati localmente.
     *
     * @return lista dei DTO dell'anagrafica utenti
     */
    List<UsersDirectoryDto> listAllUsers();
}