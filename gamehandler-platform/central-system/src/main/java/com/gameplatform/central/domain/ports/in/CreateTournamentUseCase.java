package com.gameplatform.central.domain.ports.in;

import com.gameplatform.central.domain.model.Tournament;
import com.gameplatform.shared.dto.TournamentDto;
import java.util.List;

/**
 * Caso d'uso per la creazione di un nuovo torneo.
 *
 * <p>Consente di creare un torneo specificando il modello di dominio,
 * le strutture associate e opzionalmente un identificativo di origine
 * per la tracciabilità. Supporta sia chiamate con tracciabilità tramite
 * {@code originatingRequestId} sia chiamate REST dirette.</p>
 *
 * @see com.gameplatform.central.application.service.TournamentService
 * @see com.gameplatform.central.domain.model.Tournament
 */
public interface CreateTournamentUseCase {

    /**
     * Crea un nuovo torneo a partire dal modello e dalle strutture fornite.
     *
     * @param tournament il modello di dominio del torneo da creare; non deve essere {@code null}
     * @param buildingIds la lista degli identificativi delle strutture associate al torneo; non deve essere {@code null}; se vuota il torneo non è associato ad alcuna struttura
     * @param originatingRequestId l'identificativo della richiesta origine per la tracciabilità; può essere {@code null} nel caso di chiamata REST diretta
     * @return il {@link TournamentDto} rappresentante il torneo appena creato
     * @throws IllegalArgumentException se il torneo o la lista delle strutture è {@code null}
     * @see #create(Tournament, List)
     */
    TournamentDto create(Tournament tournament, List<String> buildingIds, String originatingRequestId);

    /**
     * Crea un nuovo torneo tramite chiamata REST diretta, senza identificativo di origine.
     *
     * @param tournament il modello di dominio del torneo da creare; non deve essere {@code null}
     * @param buildingIds la lista degli identificativi delle strutture associate al torneo; non deve essere {@code null}
     * @return il {@link TournamentDto} rappresentante il torneo appena creato
     * @throws IllegalArgumentException se il torneo o la lista delle strutture è {@code null}
     * @see #create(Tournament, List, String)
     */
    default TournamentDto create(Tournament tournament, List<String> buildingIds) {
        return create(tournament, buildingIds, null);
    }
}