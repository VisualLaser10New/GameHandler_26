package com.gameplatform.central.domain.ports.in;

import java.util.List;

/**
 * Use case for querying the buildings bound to a LOCAL_ADMIN user.
 */
public interface GetLocalAdminBuildingsUseCase {

    /**
     * Restituisce gli identificativi delle strutture associate all'utente indicato.
     *
     * @param userId l'identificativo dell'utente LOCAL_ADMIN di cui recuperare le strutture; non deve essere {@code null}
     * @return la lista degli identificativi delle strutture associate; la lista è vuota se l'utente non ha strutture associate
     * @throws com.gameplatform.shared.domain.exception.UserNotFoundException se l'utente indicato non esiste
     */
    List<String> getBuildingsForUser(String userId);
}