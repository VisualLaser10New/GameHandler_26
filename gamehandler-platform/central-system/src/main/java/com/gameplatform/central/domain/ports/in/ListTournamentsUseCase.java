package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.dto.TournamentDto;
import java.util.List;

/**
 * Caso d'uso di lettura che espone l'elenco dei tornei presenti nel
 * sistema centrale, sia nella loro totalità sia filtrati per stato.
 */
public interface ListTournamentsUseCase {

    /**
     * Restituisce tutti i tornei registrati nel sistema.
     *
     * @return la lista di {@link TournamentDto} rappresentante l'intero catalogo di tornei; la lista è vuota se non esiste alcun torneo
     */
    List<TournamentDto> findAll();

    /**
     * Restituisce i tornei che si trovano nello stato indicato.
     *
     * @param status lo stato dei tornei da filtrare; non deve essere {@code null}
     * @return la lista di {@link TournamentDto} dei tornei nello stato richiesto; la lista è vuota se nessun torneo soddisfa il filtro
     */
    List<TournamentDto> findByStatus(TournamentStatus status);
}
