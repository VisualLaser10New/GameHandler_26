package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Entità JPA per la tabella {@code tournament_participants} del database MySQL.
 *
 * <p>Rappresenta l'iscrizione di un partecipante (singolo giocatore o squadra) a
 * un torneo, includendo il nome visualizzato e l'istante di registrazione.
 * Utilizza una chiave primaria composita ({@code tournament_id},
 * {@code participant_id}) tramite {@link IdClass}. Non sono dichiarate relazioni
 * JPA: torneo e partecipante sono referenziati tramite i propri identificativi
 * testuali, secondo la convenzione esagonale adottata nel progetto.</p>
 *
 * @see TournamentParticipantId
 * @see TournamentJpaEntity
 */
@Entity
@Table(name = "tournament_participants")
@IdClass(TournamentParticipantId.class)
public class TournamentParticipantJpaEntity {
    @Id
    @Column(name = "tournament_id", length = 36, nullable = false)
    private String tournamentId;
    @Id
    @Column(name = "participant_id", length = 36, nullable = false)
    private String participantId;
    @Column(name = "is_team", nullable = false)
    private Boolean isTeam;
    @Column(name = "display_name", length = 200, nullable = false)
    private String displayName;
    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    /**
     * Costruttore di default richiesto da Hibernate per la materializzazione
     * dell'entità tramite reflection.
     */
    public TournamentParticipantJpaEntity() {
    }

    /**
     * Costruisce l'iscrizione di un partecipante a un torneo.
     *
     * @param tournamentId identificativo del torneo; non deve essere {@code null}
     * @param participantId identificativo del partecipante; non deve essere {@code null}
     * @param isTeam indica se il partecipante è una squadra; non deve essere {@code null}
     * @param displayName nome visualizzato del partecipante; non deve essere {@code null}
     * @param registeredAt istante di registrazione; non deve essere {@code null}
     */
    public TournamentParticipantJpaEntity(String tournamentId, String participantId, Boolean isTeam,
                                          String displayName, Instant registeredAt) {
        this.tournamentId = tournamentId;
        this.participantId = participantId;
        this.isTeam = isTeam;
        this.displayName = displayName;
        this.registeredAt = registeredAt;
    }

    /**
     * Restituisce l'identificativo del torneo di iscrizione.
     *
     * @return l'identificativo del torneo; non deve essere {@code null}
     */
    public String getTournamentId() { return tournamentId; }

    /**
     * Imposta l'identificativo del torneo di iscrizione.
     *
     * @param tournamentId nuovo identificativo del torneo; non deve essere {@code null}
     */
    public void setTournamentId(String tournamentId) { this.tournamentId = tournamentId; }

    /**
     * Restituisce l'identificativo del partecipante iscritto.
     *
     * @return l'identificativo del partecipante; non deve essere {@code null}
     */
    public String getParticipantId() { return participantId; }

    /**
     * Imposta l'identificativo del partecipante iscritto.
     *
     * @param participantId nuovo identificativo del partecipante; non deve essere {@code null}
     */
    public void setParticipantId(String participantId) { this.participantId = participantId; }

    /**
     * Indica se il partecipante iscritto è una squadra.
     *
     * @return {@code true} se il partecipante è una squadra, {@code false} altrimenti;
     *         non deve essere {@code null}
     */
    public Boolean getIsTeam() { return isTeam; }

    /**
     * Imposta se il partecipante iscritto è una squadra.
     *
     * @param isTeam nuovo valore che indica la natura di squadra; non deve essere {@code null}
     */
    public void setIsTeam(Boolean isTeam) { this.isTeam = isTeam; }

    /**
     * Restituisce il nome visualizzato del partecipante.
     *
     * @return il nome visualizzato; non deve essere {@code null}
     */
    public String getDisplayName() { return displayName; }

    /**
     * Imposta il nome visualizzato del partecipante.
     *
     * @param displayName nuovo nome visualizzato; non deve essere {@code null}
     */
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    /**
     * Restituisce l'istante di registrazione del partecipante.
     *
     * @return l'istante di registrazione; non deve essere {@code null}
     */
    public Instant getRegisteredAt() { return registeredAt; }

    /**
     * Imposta l'istante di registrazione del partecipante.
     *
     * @param registeredAt nuovo istante di registrazione; non deve essere {@code null}
     */
    public void setRegisteredAt(Instant registeredAt) { this.registeredAt = registeredAt; }
}