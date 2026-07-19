package com.gameplatform.central.domain.ports.out;

import com.gameplatform.shared.domain.model.TournamentId;
import java.util.List;

/**
 * Porta di persistenza per l'associazione tra tornei ed edifici.
 *
 * <p>Gestisce gli edifici presso i quali un torneo viene disputato, supportando
 * il salvataggio, la consultazione e la rimozione delle associazioni per
 * torneo.</p>
 *
 * @see TournamentId
 */
public interface TournamentBuildingRepository {

    /**
     * Salva l'intero set di edifici associati al torneo indicato.
     *
     * <p>L'operazione sostituisce le associazioni precedentemente esistenti per
     * il torneo con quelle fornite.</p>
     *
     * @param tournamentId l'identificativo del torneo; non deve essere {@code null}
     * @param buildingIds  la lista degli identificativi degli edifici; non deve essere {@code null}, può essere vuota
     * @throws IllegalArgumentException se {@code tournamentId} o {@code buildingIds} sono {@code null}
     */
    void saveAll(TournamentId tournamentId, List<String> buildingIds);

    /**
     * Restituisce gli identificativi degli edifici associati al torneo indicato.
     *
     * @param tournamentId l'identificativo del torneo; non deve essere {@code null}
     * @return la lista degli edifici associati; mai {@code null}, vuota se il torneo non ha edifici associati
     * @throws IllegalArgumentException se {@code tournamentId} è {@code null}
     */
    List<String> findByTournament(TournamentId tournamentId);

    /**
     * Elimina tutte le associazioni di edifici per il torneo indicato.
     *
     * <p>Se il torneo non ha associazioni, l'operazione non ha effetto.</p>
     *
     * @param tournamentId l'identificativo del torneo; non deve essere {@code null}
     * @throws IllegalArgumentException se {@code tournamentId} è {@code null}
     */
    void deleteByTournament(TournamentId tournamentId);

    /**
     * Verifica l'esistenza dell'associazione tra il torneo e l'edificio indicati.
     *
     * @param tournamentId l'identificativo del torneo; non deve essere {@code null}
     * @param buildingId   l'identificativo dell'edificio; non deve essere {@code null}
     * @return {@code true} se l'associazione esiste, {@code false} altrimenti
     * @throws IllegalArgumentException se {@code tournamentId} o {@code buildingId} sono {@code null}
     */
    boolean existsByTournamentAndBuilding(TournamentId tournamentId, String buildingId);
}
