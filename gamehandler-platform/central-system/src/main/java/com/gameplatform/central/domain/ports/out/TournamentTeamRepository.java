package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.Team;
import com.gameplatform.shared.domain.model.TeamId;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.UserId;
import java.util.List;
import java.util.Optional;

/**
 * Porta di persistenza per le squadre dei tornei.
 *
 * <p>Gestisce il salvataggio, la ricerca e la rimozione delle squadre, incluse
 * le ricerche per nome e per appartenenza di un membro, a supporto della
 * composizione dei team nei tornei.</p>
 *
 * @see Team
 * @see TeamId
 * @see TournamentId
 * @see UserId
 */
public interface TournamentTeamRepository {

    /**
     * Salva o aggiorna la squadra fornita.
     *
     * @param team la squadra da persistere; non deve essere {@code null}
     * @return la squadra salvata, eventualmente arricchita di metadati di persistenza
     * @throws IllegalArgumentException se {@code team} è {@code null}
     */
    Team save(Team team);

    /**
     * Restituisce la squadra identificata dall'id indicato.
     *
     * @param teamId l'identificativo della squadra; non deve essere {@code null}
     * @return un {@link Optional} contenente la squadra trovata, o vuoto se assente
     * @throws IllegalArgumentException se {@code teamId} è {@code null}
     */
    Optional<Team> findById(TeamId teamId);

    /**
     * Restituisce tutte le squadre del torneo indicato.
     *
     * @param tournamentId l'identificativo del torneo; non deve essere {@code null}
     * @return la lista delle squadre del torneo; mai {@code null}, eventualmente vuota
     * @throws IllegalArgumentException se {@code tournamentId} è {@code null}
     */
    List<Team> findByTournament(TournamentId tournamentId);

    /**
     * Restituisce la squadra del torneo avente il nome indicato.
     *
     * @param tournamentId l'identificativo del torneo; non deve essere {@code null}
     * @param name         il nome della squadra; non deve essere {@code null}
     * @return un {@link Optional} contenente la squadra trovata, o vuoto se assente
     * @throws IllegalArgumentException se {@code tournamentId} o {@code name} sono {@code null}
     */
    Optional<Team> findByTournamentAndName(TournamentId tournamentId, String name);

    /**
     * Restituisce la squadra del torneo alla quale appartiene il membro indicato.
     *
     * @param tournamentId l'identificativo del torneo; non deve essere {@code null}
     * @param memberUserId l'identificativo dell'utente membro; non deve essere {@code null}
     * @return un {@link Optional} contenente la squadra trovata, o vuoto se l'utente non appartiene a nessuna squadra
     * @throws IllegalArgumentException se {@code tournamentId} o {@code memberUserId} sono {@code null}
     */
    Optional<Team> findByTournamentAndMember(TournamentId tournamentId, UserId memberUserId);

    /**
     * Verifica l'esistenza di una squadra del torneo avente il nome indicato.
     *
     * @param tournamentId l'identificativo del torneo; non deve essere {@code null}
     * @param name         il nome della squadra; non deve essere {@code null}
     * @return {@code true} se esiste una squadra con il nome, {@code false} altrimenti
     * @throws IllegalArgumentException se {@code tournamentId} o {@code name} sono {@code null}
     */
    boolean existsByTournamentAndName(TournamentId tournamentId, String name);

    /**
     * Elimina la squadra identificata dall'id indicato, se presente.
     *
     * <p>Se l'id non corrisponde ad alcuna squadra, l'operazione non ha effetto.</p>
     *
     * @param teamId l'identificativo della squadra da eliminare; non deve essere {@code null}
     * @throws IllegalArgumentException se {@code teamId} è {@code null}
     */
    void deleteById(TeamId teamId);
}
