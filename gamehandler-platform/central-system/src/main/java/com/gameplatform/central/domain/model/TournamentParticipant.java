package com.gameplatform.central.domain.model;

import com.gameplatform.shared.domain.model.TournamentId;

import java.time.Instant;
import java.util.Objects;

/**
 * Entità di dominio che rappresenta un singolo partecipante iscritto a un
 * torneo, che può essere un individuo oppure una squadra. L'identificativo del
 * partecipante fa riferimento a un utente quando {@link #isTeam()} è
 * {@code false} o a una squadra quando è {@code true}. L'identità è determinata
 * dalla coppia (identificativo torneo, identificativo partecipante).
 *
 * @see TournamentId
 * @see com.gameplatform.shared.domain.model.UserId
 * @see com.gameplatform.shared.domain.model.TeamId
 * @see Tournament
 */
public class TournamentParticipant {
    private final TournamentId tournamentId;
    private final String participantId;
    private final boolean isTeam;
    private final String displayName;
    private final Instant registeredAt;

    /**
     * Costruisce un partecipante a un torneo con i valori specificati.
     *
     * @param tournamentId identificativo del torneo a cui il partecipante è iscritto; non può essere {@code null}
     * @param participantId identificativo del partecipante (utente o squadra); non può essere {@code null} né vuoto
     * @param isTeam indica se il partecipante è una squadra ({@code true}) o un individuo ({@code false})
     * @param displayName nome visualizzato del partecipante; non può essere {@code null} né vuoto
     * @param registeredAt istante di iscrizione al torneo; non può essere {@code null}
     * @throws IllegalArgumentException se uno dei vincoli sui parametri non è rispettato
     */
    public TournamentParticipant(TournamentId tournamentId, String participantId, boolean isTeam,
                                 String displayName, Instant registeredAt) {
        if (tournamentId == null) throw new IllegalArgumentException("tournamentId cannot be null");
        if (participantId == null || participantId.isBlank()) throw new IllegalArgumentException("participantId cannot be blank");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName cannot be blank");
        if (registeredAt == null) throw new IllegalArgumentException("registeredAt cannot be null");
        this.tournamentId = tournamentId;
        this.participantId = participantId;
        this.isTeam = isTeam;
        this.displayName = displayName;
        this.registeredAt = registeredAt;
    }

    /**
     * Restituisce l'identificativo del torneo a cui il partecipante è iscritto.
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
     * Indica se il partecipante è una squadra.
     *
     * @return {@code true} se il partecipante è una squadra, {@code false} se è un individuo
     */
    public boolean isTeam() {
        return isTeam;
    }

    /**
     * Restituisce il nome visualizzato del partecipante.
     *
     * @return il nome visualizzato, mai {@code null} né vuoto
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Restituisce l'istante di iscrizione al torneo.
     *
     * @return l'istante di iscrizione, mai {@code null}
     */
    public Instant getRegisteredAt() {
        return registeredAt;
    }

    /**
     * Confronta questo partecipante con un altro oggetto verificandone
     * l'uguaglianza sulla base della coppia torneo e identificativo del
     * partecipante.
     *
     * @param o oggetto da confrontare; può essere {@code null}
     * @return {@code true} se l'oggetto è un {@code TournamentParticipant} con lo stesso torneo e lo stesso identificativo, {@code false} altrimenti
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TournamentParticipant that = (TournamentParticipant) o;
        return Objects.equals(tournamentId, that.tournamentId) && Objects.equals(participantId, that.participantId);
    }

    /**
     * Restituisce il codice hash calcolato sulla coppia torneo e identificativo
     * del partecipante.
     *
     * @return il codice hash del partecipante
     */
    @Override
    public int hashCode() {
        return Objects.hash(tournamentId, participantId);
    }
}
