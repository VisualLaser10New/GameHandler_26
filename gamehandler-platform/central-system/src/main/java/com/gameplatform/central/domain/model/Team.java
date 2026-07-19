package com.gameplatform.central.domain.model;

import com.gameplatform.shared.domain.model.TeamId;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Entità di dominio che rappresenta una squadra registrata all'interno di un
 * torneo, con il proprio nome, l'elenco dei membri e l'istante di creazione.
 * L'identità è determinata dall'identificativo della squadra. L'entità è
 * immutabile: l'elenco dei membri viene copiato in fase di costruzione ed
 * esposto in sola lettura.
 *
 * @see TeamId
 * @see TournamentId
 * @see UserId
 * @see Tournament
 */
public class Team {
    private final TeamId teamId;
    private final TournamentId tournamentId;
    private final String name;
    private final List<UserId> members;
    private final Instant createdAt;

    /**
     * Costruisce una squadra con i valori specificati.
     *
     * @param teamId identificativo univoco della squadra; non può essere {@code null}
     * @param tournamentId identificativo del torneo a cui la squadra è iscritta; non può essere {@code null}
     * @param name nome della squadra; non può essere {@code null} né vuoto
     * @param members elenco degli identificativi dei membri; non può essere {@code null}, ma può essere vuoto
     * @param createdAt istante di creazione della squadra; non può essere {@code null}
     * @throws IllegalArgumentException se uno dei vincoli sui parametri non è rispettato
     */
    public Team(TeamId teamId, TournamentId tournamentId, String name, List<UserId> members, Instant createdAt) {
        if (teamId == null) throw new IllegalArgumentException("teamId cannot be null");
        if (tournamentId == null) throw new IllegalArgumentException("tournamentId cannot be null");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name cannot be blank");
        if (members == null) throw new IllegalArgumentException("members cannot be null");
        if (createdAt == null) throw new IllegalArgumentException("createdAt cannot be null");
        this.teamId = teamId;
        this.tournamentId = tournamentId;
        this.name = name;
        this.members = List.copyOf(members);
        this.createdAt = createdAt;
    }

    /**
     * Restituisce l'identificativo univoco della squadra.
     *
     * @return l'identificativo della squadra, mai {@code null}
     */
    public TeamId getTeamId() {
        return teamId;
    }

    /**
     * Restituisce l'identificativo del torneo a cui la squadra è iscritta.
     *
     * @return l'identificativo del torneo, mai {@code null}
     */
    public TournamentId getTournamentId() {
        return tournamentId;
    }

    /**
     * Restituisce il nome della squadra.
     *
     * @return il nome della squadra, mai {@code null} né vuoto
     */
    public String getName() {
        return name;
    }

    /**
     * Restituisce l'elenco immutabile degli identificativi dei membri.
     *
     * @return la lista non modificabile dei membri, mai {@code null} ma eventualmente vuota
     */
    public List<UserId> getMembers() {
        return members;
    }

    /**
     * Restituisce l'istante di creazione della squadra.
     *
     * @return l'istante di creazione, mai {@code null}
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Confronta questa squadra con un altro oggetto verificandone l'uguaglianza
     * sulla base dell'identificativo della squadra.
     *
     * @param o oggetto da confrontare; può essere {@code null}
     * @return {@code true} se l'oggetto è un {@code Team} con lo stesso identificativo, {@code false} altrimenti
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Team that = (Team) o;
        return Objects.equals(teamId, that.teamId);
    }

    /**
     * Restituisce il codice hash calcolato sull'identificativo della squadra.
     *
     * @return il codice hash della squadra
     */
    @Override
    public int hashCode() {
        return Objects.hash(teamId);
    }
}
