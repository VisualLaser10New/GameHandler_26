package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.dto.TournamentSummaryDto;

import java.util.List;

/**
 * Use case per la lettura dei riepiloghi dei tornei. Restituisce le
 * righe della tabella locale di riepilogo tornei, opzionalmente filtrate
 * per stato. Vengono restituiti solo i tornei non eliminati.
 *
 * @see com.gameplatform.shared.dto.TournamentSummaryDto
 */
public interface ListTournamentSummariesUseCase {

    /**
     * Restituisce i riepiloghi dei tornei, filtrati opzionalmente per stato.
     *
     * @param statusFilter stato del torneo per filtrare i risultati, oppure null per nessun filtro
     * @return lista dei DTO di riepilogo tornei
     */
    List<TournamentSummaryDto> listSummaries(TournamentStatus statusFilter);
}