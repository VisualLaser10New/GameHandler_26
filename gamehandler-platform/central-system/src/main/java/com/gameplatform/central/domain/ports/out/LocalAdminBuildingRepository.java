package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.LocalAdminBuilding;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.UserId;

import java.util.List;

/**
 * Porta di persistenza per la tabella di associazione {@code local_admin_buildings}.
 *
 * <p>La tabella costituisce la fonte di verità centrale per la relazione tra
 * amministratori locali e edifici. Le righe persistite sono inserite o aggiornate
 * tramite chiave primaria composta {@code (user_id, building_id)}, rendendo le
 * operazioni dei chiamanti naturalmente idempotenti.</p>
 *
 * @see LocalAdminBuilding
 * @see UserId
 * @see BuildingId
 */
public interface LocalAdminBuildingRepository {

    /**
     * Inserisce o aggiorna l'associazione tra amministratore locale ed edificio.
     *
     * @param binding l'associazione da persistere, identificata dalla chiave composta; non deve essere {@code null}
     * @return l'associazione salvata, eventualmente arricchita di metadati di persistenza
     * @throws IllegalArgumentException se {@code binding} è {@code null}
     */
    LocalAdminBuilding save(LocalAdminBuilding binding);

    /**
     * Verifica l'esistenza dell'associazione tra l'utente e l'edificio indicati.
     *
     * @param userId     l'identificativo dell'utente; non deve essere {@code null}
     * @param buildingId l'identificativo dell'edificio; non deve essere {@code null}
     * @return {@code true} se l'associazione esiste, {@code false} altrimenti
     * @throws IllegalArgumentException se {@code userId} o {@code buildingId} sono {@code null}
     */
    boolean existsByUserIdAndBuildingId(UserId userId, BuildingId buildingId);

    /**
     * Elimina l'associazione tra l'utente e l'edificio indicati, se presente.
     *
     * <p>Se l'associazione non esiste, l'operazione non ha effetto.</p>
     *
     * @param userId     l'identificativo dell'utente; non deve essere {@code null}
     * @param buildingId l'identificativo dell'edificio; non deve essere {@code null}
     * @throws IllegalArgumentException se {@code userId} o {@code buildingId} sono {@code null}
     */
    void deleteByUserIdAndBuildingId(UserId userId, BuildingId buildingId);

    /**
     * Restituisce tutte le associazioni relative all'utente indicato.
     *
     * @param userId l'identificativo dell'utente; non deve essere {@code null}
     * @return la lista delle associazioni dell'utente; mai {@code null}, vuota se l'utente non ha associazioni
     * @throws IllegalArgumentException se {@code userId} è {@code null}
     */
    List<LocalAdminBuilding> findByUserId(UserId userId);
}
