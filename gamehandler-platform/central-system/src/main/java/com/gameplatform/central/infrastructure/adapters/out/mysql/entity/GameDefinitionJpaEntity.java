package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Entità JPA per la tabella sorgente di verità {@code game_definitions} (FASE 2,
 * GAME_ADMIN).
 *
 * <p>La chiave primaria è la business key {@code game_type} (memorizzata come
 * nome dell'enum {@link com.gameplatform.shared.domain.model.GameType}), pertanto
 * non viene utilizzata alcuna {@code @GeneratedValue}. Non sono dichiarate
 * relazioni JPA: la colonna JSON {@code registration_rules} è mantenuta come
 * semplice {@code String} e convertita da Map a JSON nel {@code GameDefinitionMapper},
 * secondo quanto previsto da RNF-08. Non espone metodi che lanciano eccezioni
 * checked: eventuali violazioni di vincoli (es. {@code game_type} duplicato) sono
 * rilevate al momento della persistenza.</p>
 *
 * @see GameDefinitionJpaEntity#getRegistrationRulesJson()
 */
@Entity
@Table(name = "game_definitions")
public class GameDefinitionJpaEntity {

    @Id
    @Column(name = "game_type", length = 50, nullable = false)
    private String gameType;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "min_players", nullable = false)
    private Integer minPlayers;

    @Column(name = "max_players", nullable = false)
    private Integer maxPlayers;

    @Column(name = "team_allowed", nullable = false)
    private Boolean teamAllowed;

    @Column(name = "registration_rules", columnDefinition = "json")
    private String registrationRulesJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Costruttore di default richiesto da Hibernate per la materializzazione
     * dell'entità tramite reflection.
     */
    public GameDefinitionJpaEntity() {
    }

    /**
     * Costruisce la definizione completa di un gioco con i metadati e le regole
     * di registrazione fornite.
     *
     * @param gameType business key che identifica univocamente il tipo di gioco; non deve essere {@code null}
     * @param name nome descrittivo del gioco; non deve essere {@code null}
     * @param minPlayers numero minimo di giocatori ammessi; non deve essere {@code null}
     * @param maxPlayers numero massimo di giocatori ammessi; non deve essere {@code null}
     * @param teamAllowed indica se il gioco consente squadre; non deve essere {@code null}
     * @param registrationRulesJson regole di registrazione in formato JSON; può essere {@code null}
     * @param createdAt istante di creazione della definizione; non deve essere {@code null}
     * @param updatedAt istante dell'ultimo aggiornamento della definizione; non deve essere {@code null}
     */
    public GameDefinitionJpaEntity(String gameType, String name, Integer minPlayers, Integer maxPlayers,
                                   Boolean teamAllowed, String registrationRulesJson,
                                   Instant createdAt, Instant updatedAt) {
        this.gameType = gameType;
        this.name = name;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.teamAllowed = teamAllowed;
        this.registrationRulesJson = registrationRulesJson;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Restituisce la business key che identifica il tipo di gioco.
     *
     * @return il tipo di gioco; non deve essere {@code null}
     */
    public String getGameType() {
        return gameType;
    }

    /**
     * Imposta la business key che identifica il tipo di gioco.
     *
     * @param gameType nuovo tipo di gioco; non deve essere {@code null}
     */
    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    /**
     * Restituisce il nome descrittivo del gioco.
     *
     * @return il nome del gioco; non deve essere {@code null}
     */
    public String getName() {
        return name;
    }

    /**
     * Imposta il nome descrittivo del gioco.
     *
     * @param name nuovo nome del gioco; non deve essere {@code null}
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Restituisce il numero minimo di giocatori ammessi per il gioco.
     *
     * @return il numero minimo di giocatori; non deve essere {@code null}
     */
    public Integer getMinPlayers() {
        return minPlayers;
    }

    /**
     * Imposta il numero minimo di giocatori ammessi per il gioco.
     *
     * @param minPlayers nuovo numero minimo di giocatori; non deve essere {@code null}
     */
    public void setMinPlayers(Integer minPlayers) {
        this.minPlayers = minPlayers;
    }

    /**
     * Restituisce il numero massimo di giocatori ammessi per il gioco.
     *
     * @return il numero massimo di giocatori; non deve essere {@code null}
     */
    public Integer getMaxPlayers() {
        return maxPlayers;
    }

    /**
     * Imposta il numero massimo di giocatori ammessi per il gioco.
     *
     * @param maxPlayers nuovo numero massimo di giocatori; non deve essere {@code null}
     */
    public void setMaxPlayers(Integer maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    /**
     * Indica se il gioco consente la formazione di squadre.
     *
     * @return {@code true} se le squadre sono ammesse, {@code false} altrimenti;
     *         non deve essere {@code null}
     */
    public Boolean getTeamAllowed() {
        return teamAllowed;
    }

    /**
     * Imposta se il gioco consente la formazione di squadre.
     *
     * @param teamAllowed nuovo valore che indica l'ammissione delle squadre; non deve essere {@code null}
     */
    public void setTeamAllowed(Boolean teamAllowed) {
        this.teamAllowed = teamAllowed;
    }

    /**
     * Restituisce le regole di registrazione del gioco in formato JSON.
     *
     * @return le regole di registrazione come stringa JSON; può essere {@code null}
     */
    public String getRegistrationRulesJson() {
        return registrationRulesJson;
    }

    /**
     * Imposta le regole di registrazione del gioco in formato JSON.
     *
     * @param registrationRulesJson nuove regole di registrazione come stringa JSON; può essere {@code null}
     */
    public void setRegistrationRulesJson(String registrationRulesJson) {
        this.registrationRulesJson = registrationRulesJson;
    }

    /**
     * Restituisce l'istante di creazione della definizione del gioco.
     *
     * @return l'istante di creazione; non deve essere {@code null}
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Imposta l'istante di creazione della definizione del gioco.
     *
     * @param createdAt nuovo istante di creazione; non deve essere {@code null}
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Restituisce l'istante dell'ultimo aggiornamento della definizione del gioco.
     *
     * @return l'istante dell'ultimo aggiornamento; non deve essere {@code null}
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Imposta l'istante dell'ultimo aggiornamento della definizione del gioco.
     *
     * @param updatedAt nuovo istante dell'ultimo aggiornamento; non deve essere {@code null}
     */
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
