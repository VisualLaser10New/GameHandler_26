package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.LocalAdminBuilding;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.UserId;

import java.util.List;

/**
 * Repository out-port per la gestione delle associazioni tra amministratori locali ed edifici.
 * <p>
 * Consente di associare un utente amministratore a uno o pi&ugrave; edifici,
 * definendone i permessi di gestione a livello di sede locale.
 * </p>
 *
 * @see LocalAdminBuilding
 */
public interface LocalAdminBuildingLocalRepository {
    /**
     * Salva o aggiorna un'associazione tra amministratore ed edificio.
     *
     * @param binding l'associazione da persistere
     * @return l'associazione persistita
     */
    LocalAdminBuilding save(LocalAdminBuilding binding);

    /**
     * Verifica se esiste gi&agrave; un'associazione tra l'utente e l'edificio specificati.
     *
     * @param userId     l'identificativo dell'utente
     * @param buildingId l'identificativo dell'edificio
     * @return {@code true} se l'associazione esiste, {@code false} altrimenti
     */
    boolean existsByUserIdAndBuildingId(UserId userId, BuildingId buildingId);

    /**
     * Elimina l'associazione tra l'utente e l'edificio specificati.
     *
     * @param userId     l'identificativo dell'utente
     * @param buildingId l'identificativo dell'edificio
     */
    void deleteByUserIdAndBuildingId(UserId userId, BuildingId buildingId);

    /**
     * Restituisce tutte le associazioni per un determinato utente.
     *
     * @param userId l'identificativo dell'utente
     * @return la lista delle associazioni dell'utente specificato
     */
    List<LocalAdminBuilding> findByUserId(UserId userId);
}