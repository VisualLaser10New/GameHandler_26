package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity for {@code tournament_participants_local} (PIANO §7.B).
 * Read-only replica updated only by sync; composite PK
 * ({@code tournamentId}, {@code participantId}) via
 * {@link TournamentParticipantLocalId}; no {@code @OneToMany}, no
 * {@code @Version} (mirror of {@code TournamentMatchLocalJpaEntity}).
 */
@Entity
@Table(name = "tournament_participants_local", indexes = {
        @Index(name = "idx_tpl_tournament", columnList = "tournament_id")
})
@IdClass(TournamentParticipantLocalId.class)
public class TournamentParticipantLocalJpaEntity {

    @Id
    @Column(name = "tournament_id", length = 36, nullable = false)
    private String tournamentId;

    @Id
    @Column(name = "participant_id", length = 64, nullable = false)
    private String participantId;

    @Column(name = "is_team", nullable = false)
    private Boolean isTeam;

    @Column(name = "display_name", length = 100, nullable = false)
    private String displayName;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Costruttore predefinito richiesto da JPA.
     */
    public TournamentParticipantLocalJpaEntity() {
    }

    /**
     * Costruisce un nuovo partecipante torneo locale.
     *
     * @param tournamentId  identificativo del torneo
     * @param participantId identificativo del partecipante
     * @param isTeam        indica se il partecipante è una squadra
     * @param displayName   nome visualizzato del partecipante
     * @param registeredAt  istante di registrazione
     * @param updatedAt     istante dell'ultimo aggiornamento
     */
    public TournamentParticipantLocalJpaEntity(String tournamentId, String participantId, Boolean isTeam,
                                               String displayName, Instant registeredAt, Instant updatedAt) {
        this.tournamentId = tournamentId;
        this.participantId = participantId;
        this.isTeam = isTeam;
        this.displayName = displayName;
        this.registeredAt = registeredAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Restituisce l'identificativo del torneo.
     *
     * @return tournamentId
     */
    public String getTournamentId() {
        return tournamentId;
    }

    /**
     * Imposta l'identificativo del torneo.
     *
     * @param tournamentId nuovo identificativo torneo
     */
    public void setTournamentId(String tournamentId) {
        this.tournamentId = tournamentId;
    }

    /**
     * Restituisce l'identificativo del partecipante.
     *
     * @return participantId
     */
    public String getParticipantId() {
        return participantId;
    }

    /**
     * Imposta l'identificativo del partecipante.
     *
     * @param participantId nuovo identificativo partecipante
     */
    public void setParticipantId(String participantId) {
        this.participantId = participantId;
    }

    /**
     * Indica se il partecipante è una squadra.
     *
     * @return {@code true} se è una squadra
     */
    public Boolean getIsTeam() {
        return isTeam;
    }

    /**
     * Imposta se il partecipante è una squadra.
     *
     * @param isTeam {@code true} per indicare una squadra
     */
    public void setIsTeam(Boolean isTeam) {
        this.isTeam = isTeam;
    }

    /**
     * Restituisce il nome visualizzato del partecipante.
     *
     * @return displayName
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Imposta il nome visualizzato del partecipante.
     *
     * @param displayName nuovo nome visualizzato
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Restituisce l'istante di registrazione del partecipante.
     *
     * @return registeredAt
     */
    public Instant getRegisteredAt() {
        return registeredAt;
    }

    /**
     * Imposta l'istante di registrazione.
     *
     * @param registeredAt nuovo istante di registrazione
     */
    public void setRegisteredAt(Instant registeredAt) {
        this.registeredAt = registeredAt;
    }

    /**
     * Restituisce l'istante dell'ultimo aggiornamento.
     *
     * @return updatedAt
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Imposta l'istante dell'ultimo aggiornamento.
     *
     * @param updatedAt nuovo istante di aggiornamento
     */
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
