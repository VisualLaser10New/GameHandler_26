package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Entità JPA per la tabella {@code tournament_buildings} del database MySQL.
 *
 * <p>Rappresenta l'associazione tra un torneo e gli edifici in cui esso si svolge,
 * modellata come tabella di legame. Utilizza una chiave primaria composita
 * ({@code tournament_id}, {@code building_id}) tramite {@link IdClass}. Non sono
 * dichiarate relazioni JPA: torneo ed edificio sono referenziati tramite i propri
 * identificativi testuali, secondo la convenzione esagonale adottata nel progetto.</p>
 *
 * @see TournamentBuildingId
 * @see TournamentJpaEntity
 */
@Entity
@Table(name = "tournament_buildings")
@IdClass(TournamentBuildingId.class)
public class TournamentBuildingJpaEntity {
    @Id
    @Column(name = "tournament_id", length = 36, nullable = false)
    private String tournamentId;
    @Id
    @Column(name = "building_id", length = 100, nullable = false)
    private String buildingId;

    /**
     * Costruttore di default richiesto da Hibernate per la materializzazione
     * dell'entità tramite reflection.
     */
    public TournamentBuildingJpaEntity() {
    }

    /**
     * Costruisce l'associazione tra un torneo e un edificio.
     *
     * @param tournamentId identificativo del torneo; non deve essere {@code null}
     * @param buildingId identificativo dell'edificio associato; non deve essere {@code null}
     */
    public TournamentBuildingJpaEntity(String tournamentId, String buildingId) {
        this.tournamentId = tournamentId;
        this.buildingId = buildingId;
    }

    /**
     * Restituisce l'identificativo del torneo associato.
     *
     * @return l'identificativo del torneo; non deve essere {@code null}
     */
    public String getTournamentId() { return tournamentId; }

    /**
     * Imposta l'identificativo del torneo associato.
     *
     * @param tournamentId nuovo identificativo del torneo; non deve essere {@code null}
     */
    public void setTournamentId(String tournamentId) { this.tournamentId = tournamentId; }

    /**
     * Restituisce l'identificativo dell'edificio associato.
     *
     * @return l'identificativo dell'edificio; non deve essere {@code null}
     */
    public String getBuildingId() { return buildingId; }

    /**
     * Imposta l'identificativo dell'edificio associato.
     *
     * @param buildingId nuovo identificativo dell'edificio; non deve essere {@code null}
     */
    public void setBuildingId(String buildingId) { this.buildingId = buildingId; }
}