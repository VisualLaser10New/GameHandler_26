package com.gameplatform.central.domain.ports.in;

import java.util.List;

/**
 * Use case for assigning (binding) and revoking (unbinding) buildings to/from a
 * LOCAL_ADMIN user.
 *
 * <p>Operations are idempotent: assigning an already-bound building is a silent
 * no-op; revoking a non-existent binding is a silent no-op. Each effective
 * assign/revoke writes a corresponding outbox event
 * ({@code LOCAL_ADMIN_BUILDING_ASSIGNED} / {@code LOCAL_ADMIN_BUILDING_REVOKED})
 * so the change is replicated to every active Local Server.</p>
 */
public interface AssignLocalAdminBuildingsUseCase {

    /**
     * Associa le strutture indicate all'utente LOCAL_ADMIN fornito.
     *
     * @param userId l'identificativo dell'utente LOCAL_ADMIN; non deve essere {@code null}
     * @param buildingIds la lista degli identificativi delle strutture da associare; non deve essere {@code null}; se vuota non viene effettuata alcuna operazione
     * @throws com.gameplatform.shared.domain.exception.UserNotFoundException se l'utente indicato non esiste
     * @see #revokeBuildings(String, List)
     */
    void assignBuildings(String userId, List<String> buildingIds);

    /**
     * Revoca l'associazione delle strutture indicate dall'utente LOCAL_ADMIN fornito.
     *
     * @param userId l'identificativo dell'utente LOCAL_ADMIN; non deve essere {@code null}
     * @param buildingIds la lista degli identificativi delle strutture da dissociare; non deve essere {@code null}; se vuota non viene effettuata alcuna operazione
     * @throws com.gameplatform.shared.domain.exception.UserNotFoundException se l'utente indicato non esiste
     * @see #assignBuildings(String, List)
     */
    void revokeBuildings(String userId, List<String> buildingIds);
}