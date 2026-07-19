package com.gameplatform.central.domain.model;

import com.gameplatform.shared.domain.model.TournamentId;

import java.util.Objects;

/**
 * Modello di lettura di dominio che rappresenta la riga di classifica cumulativa
 * di un singolo partecipante all'interno di un torneo, con il conteggio di
 * vittorie, sconfitte, punti e la posizione in graduatoria. L'identità è
 * determinata dalla coppia (identificativo torneo, identificativo partecipante);
 * la posizione è opzionale finché non viene assegnata la graduatoria definitiva.
 *
 * @see TournamentId
 * @see Tournament
 * @see TournamentParticipant
 */
public class TournamentStanding {
    private final TournamentId tournamentId;
    private final String participantId;
    private final int wins;
    private final int losses;
    private final int points;
    private final Integer rank;

    /**
     * Costruisce una riga di classifica con i valori specificati.
     *
     * @param tournamentId identificativo del torneo a cui la classifica si riferisce; non può essere {@code null}
     * @param participantId identificativo del partecipante; non può essere {@code null} né vuoto
     * @param wins numero di vittorie; non può essere negativo
     * @param losses numero di sconfitte; non può essere negativo
     * @param points punteggio accumulato; non può essere negativo
     * @param rank posizione in graduatoria; può essere {@code null} se non ancora assegnata
     * @throws IllegalArgumentException se uno dei vincoli sui parametri non è rispettato
     */
    public TournamentStanding(TournamentId tournamentId, String participantId, int wins, int losses,
                              int points, Integer rank) {
        if (tournamentId == null) throw new IllegalArgumentException("tournamentId cannot be null");
        if (participantId == null || participantId.isBlank()) throw new IllegalArgumentException("participantId cannot be blank");
        if (wins < 0) throw new IllegalArgumentException("wins must be >= 0");
        if (losses < 0) throw new IllegalArgumentException("losses must be >= 0");
        if (points < 0) throw new IllegalArgumentException("points must be >= 0");
        this.tournamentId = tournamentId;
        this.participantId = participantId;
        this.wins = wins;
        this.losses = losses;
        this.points = points;
        this.rank = rank;
    }

    /**
     * Restituisce l'identificativo del torneo a cui la classifica si riferisce.
     *
     * @return l'identificativo del torneo, mai {@code null}
     */
    public TournamentId getTournamentId() {
        return tournamentId;
    }

    /**
     * Restituisce l'identificativo del partecipante.
     *
     * @return l'identificativo del partecipante, mai {@code null} né vuoto
     */
    public String getParticipantId() {
        return participantId;
    }

    /**
     * Restituisce il numero di vittorie del partecipante.
     *
     * @return il numero di vittorie, sempre maggiore o uguale a zero
     */
    public int getWins() {
        return wins;
    }

    /**
     * Restituisce il numero di sconfitte del partecipante.
     *
     * @return il numero di sconfitte, sempre maggiore o uguale a zero
     */
    public int getLosses() {
        return losses;
    }

    /**
     * Restituisce il punteggio accumulato dal partecipante.
     *
     * @return il punteggio, sempre maggiore o uguale a zero
     */
    public int getPoints() {
        return points;
    }

    /**
     * Restituisce la posizione del partecipante in graduatoria.
     *
     * @return la posizione in graduatoria, oppure {@code null} se non ancora assegnata
     */
    public Integer getRank() {
        return rank;
    }

    /**
     * Confronta questa riga di classifica con un altro oggetto verificandone
     * l'uguaglianza sulla base della coppia torneo e identificativo del
     * partecipante.
     *
     * @param o oggetto da confrontare; può essere {@code null}
     * @return {@code true} se l'oggetto è un {@code TournamentStanding} con lo stesso torneo e lo stesso partecipante, {@code false} altrimenti
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TournamentStanding that = (TournamentStanding) o;
        return Objects.equals(tournamentId, that.tournamentId) && Objects.equals(participantId, that.participantId);
    }

    /**
     * Restituisce il codice hash calcolato sulla coppia torneo e identificativo
     * del partecipante.
     *
     * @return il codice hash della riga di classifica
     */
    @Override
    public int hashCode() {
        return Objects.hash(tournamentId, participantId);
    }
}
