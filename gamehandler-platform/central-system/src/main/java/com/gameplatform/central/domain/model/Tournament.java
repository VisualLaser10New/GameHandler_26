package com.gameplatform.central.domain.model;

import com.gameplatform.central.domain.exception.InvalidTournamentStateException;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentFormat;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.domain.model.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * Entità di dominio che rappresenta un torneo e ne incarna il ciclo di vita
 * come macchina a stati immutabile, dalla bozza fino al completamento, con la
 * possibilità di annullamento nelle fasi iniziali. L'identità è determinata
 * dall'identificativo del torneo. I metodi di transizione restituiscono una
 * nuova istanza con lo stato aggiornato senza modificare quella corrente e
 * segnalano gli stati di partenza non validi tramite eccezione.
 *
 * @see TournamentId
 * @see TournamentStatus
 * @see TournamentFormat
 * @see InvalidTournamentStateException
 */
public class Tournament {
    private final TournamentId tournamentId;
    private final String name;
    private final GameType gameType;
    private final boolean teamBased;
    private final int teamSize;
    private final TournamentFormat format;
    private final TournamentStatus status;
    private final Instant startsAt;
    private final Instant endsAt;
    private final UserId createdBy;
    private final Instant createdAt;

    /**
     * Costruisce un torneo con i valori specificati.
     *
     * @param tournamentId identificativo univoco del torneo; non può essere {@code null}
     * @param name nome del torneo; non può essere {@code null} né vuoto
     * @param gameType tipo di gioco del torneo; non può essere {@code null}
     * @param teamBased indica se il torneo è a squadre
     * @param teamSize dimensione delle squadre; deve essere maggiore o uguale a 1 e pari a 1 per i tornei individuali
     * @param format formato del torneo; non può essere {@code null}
     * @param status stato iniziale del torneo; non può essere {@code null}
     * @param startsAt istante di inizio previsto; non può essere {@code null}
     * @param endsAt istante di fine; può essere {@code null} se il torneo non è ancora concluso
     * @param createdBy identificativo dell'utente che ha creato il torneo; non può essere {@code null}
     * @param createdAt istante di creazione del torneo; non può essere {@code null}
     * @throws IllegalArgumentException se uno dei vincoli sui parametri non è rispettato
     */
    public Tournament(TournamentId tournamentId, String name, GameType gameType, boolean teamBased,
                      int teamSize, TournamentFormat format, TournamentStatus status, Instant startsAt,
                      Instant endsAt, UserId createdBy, Instant createdAt) {
        if (tournamentId == null) throw new IllegalArgumentException("tournamentId cannot be null");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name cannot be blank");
        if (gameType == null) throw new IllegalArgumentException("gameType cannot be null");
        if (format == null) throw new IllegalArgumentException("format cannot be null");
        if (status == null) throw new IllegalArgumentException("status cannot be null");
        if (teamSize < 1) throw new IllegalArgumentException("teamSize must be >= 1");
        if (!teamBased && teamSize != 1) throw new IllegalArgumentException("Individual tournament must have teamSize == 1");
        if (startsAt == null) throw new IllegalArgumentException("startsAt cannot be null");
        if (createdBy == null) throw new IllegalArgumentException("createdBy cannot be null");
        if (createdAt == null) throw new IllegalArgumentException("createdAt cannot be null");
        this.tournamentId = tournamentId;
        this.name = name;
        this.gameType = gameType;
        this.teamBased = teamBased;
        this.teamSize = teamSize;
        this.format = format;
        this.status = status;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    /**
     * Restituisce un nuovo torneo con stato
     * {@link TournamentStatus#OPEN_REGISTRATION}. La transizione è consentita
     * solo se lo stato corrente è {@link TournamentStatus#DRAFT}; l'istanza
     * corrente non viene modificata.
     *
     * @return una nuova istanza immutabile di {@code Tournament} nello stato {@code OPEN_REGISTRATION}
     * @throws InvalidTournamentStateException se lo stato corrente non è {@code DRAFT}
     * @see #cancel()
     * @see #startProgress()
     */
    public Tournament openRegistration() {
        if (status != TournamentStatus.DRAFT) {
            throw new InvalidTournamentStateException("Cannot open registration from status " + status);
        }
        return new Tournament(tournamentId, name, gameType, teamBased, teamSize, format,
                TournamentStatus.OPEN_REGISTRATION, startsAt, endsAt, createdBy, createdAt);
    }

    /**
     * Restituisce un nuovo torneo con stato
     * {@link TournamentStatus#CANCELLED}. La transizione è consentita solo se
     * lo stato corrente è {@link TournamentStatus#DRAFT} oppure
     * {@link TournamentStatus#OPEN_REGISTRATION}; l'istanza corrente non viene
     * modificata.
     *
     * @return una nuova istanza immutabile di {@code Tournament} nello stato {@code CANCELLED}
     * @throws InvalidTournamentStateException se lo stato corrente non è né {@code DRAFT} né {@code OPEN_REGISTRATION}
     */
    public Tournament cancel() {
        if (status != TournamentStatus.DRAFT && status != TournamentStatus.OPEN_REGISTRATION) {
            throw new InvalidTournamentStateException("Cannot cancel from status " + status);
        }
        return new Tournament(tournamentId, name, gameType, teamBased, teamSize, format,
                TournamentStatus.CANCELLED, startsAt, endsAt, createdBy, createdAt);
    }

    /**
     * Restituisce un nuovo torneo con stato
     * {@link TournamentStatus#IN_PROGRESS}. La transizione è consentita solo se
     * lo stato corrente è {@link TournamentStatus#OPEN_REGISTRATION}; l'istanza
     * corrente non viene modificata.
     *
     * @return una nuova istanza immutabile di {@code Tournament} nello stato {@code IN_PROGRESS}
     * @throws InvalidTournamentStateException se lo stato corrente non è {@code OPEN_REGISTRATION}
     * @see #complete(Instant)
     */
    public Tournament startProgress() {
        if (status != TournamentStatus.OPEN_REGISTRATION) {
            throw new InvalidTournamentStateException("Cannot start progress from status " + status);
        }
        return new Tournament(tournamentId, name, gameType, teamBased, teamSize, format,
                TournamentStatus.IN_PROGRESS, startsAt, endsAt, createdBy, createdAt);
    }

    /**
     * Restituisce un nuovo torneo con stato
     * {@link TournamentStatus#COMPLETED} e istante di fine impostato a
     * {@code endedAt}. La transizione è consentita solo se lo stato corrente è
     * {@link TournamentStatus#IN_PROGRESS} e {@code endedAt} non è {@code null};
     * l'istanza corrente non viene modificata.
     *
     * @param endedAt istante di effettiva conclusione del torneo; non può essere {@code null}
     * @return una nuova istanza immutabile di {@code Tournament} nello stato {@code COMPLETED} con istante di fine pari a {@code endedAt}
     * @throws InvalidTournamentStateException se lo stato corrente non è {@code IN_PROGRESS} oppure se {@code endedAt} è {@code null}
     * @see #startProgress()
     */
    public Tournament complete(Instant endedAt) {
        if (status != TournamentStatus.IN_PROGRESS || endedAt == null) {
            throw new InvalidTournamentStateException("Cannot complete from status " + status);
        }
        return new Tournament(tournamentId, name, gameType, teamBased, teamSize, format,
                TournamentStatus.COMPLETED, startsAt, endedAt, createdBy, createdAt);
    }

    /**
     * Restituisce un nuovo torneo con il nome e l'istante di inizio aggiornati e
     * l'istante di fine azzerato. La transizione è consentita solo se lo stato
     * corrente è {@link TournamentStatus#DRAFT}; l'istanza corrente non viene
     * modificata.
     *
     * @param name nuovo nome del torneo
     * @param startsAt nuovo istante di inizio previsto
     * @return una nuova istanza immutabile di {@code Tournament} nello stato {@code DRAFT} con nome e istante di inizio aggiornati e istante di fine pari a {@code null}
     * @throws InvalidTournamentStateException se lo stato corrente non è {@code DRAFT}
     */
    public Tournament update(String name, Instant startsAt) {
        if (status != TournamentStatus.DRAFT) {
            throw new InvalidTournamentStateException("Cannot update from status " + status);
        }
        return new Tournament(tournamentId, name, gameType, teamBased, teamSize, format,
                status, startsAt, null, createdBy, createdAt);
    }

    /**
     * Restituisce l'identificativo univoco del torneo.
     *
     * @return l'identificativo del torneo, mai {@code null}
     */
    public TournamentId getTournamentId() {
        return tournamentId;
    }

    /**
     * Restituisce il nome del torneo.
     *
     * @return il nome del torneo, mai {@code null} né vuoto
     */
    public String getName() {
        return name;
    }

    /**
     * Restituisce il tipo di gioco del torneo.
     *
     * @return il tipo di gioco, mai {@code null}
     */
    public GameType getGameType() {
        return gameType;
    }

    /**
     * Indica se il torneo è organizzato a squadre.
     *
     * @return {@code true} se il torneo è a squadre, {@code false} se è individuale
     */
    public boolean isTeamBased() {
        return teamBased;
    }

    /**
     * Restituisce la dimensione delle squadre partecipanti.
     *
     * @return la dimensione delle squadre, sempre maggiore o uguale a 1
     */
    public int getTeamSize() {
        return teamSize;
    }

    /**
     * Restituisce il formato del torneo.
     *
     * @return il formato del torneo, mai {@code null}
     */
    public TournamentFormat getFormat() {
        return format;
    }

    /**
     * Restituisce lo stato corrente del torneo.
     *
     * @return lo stato del torneo, mai {@code null}
     */
    public TournamentStatus getStatus() {
        return status;
    }

    /**
     * Restituisce l'istante di inizio previsto del torneo.
     *
     * @return l'istante di inizio, mai {@code null}
     */
    public Instant getStartsAt() {
        return startsAt;
    }

    /**
     * Restituisce l'istante di fine del torneo.
     *
     * @return l'istante di fine, oppure {@code null} se il torneo non è ancora concluso
     */
    public Instant getEndsAt() {
        return endsAt;
    }

    /**
     * Restituisce l'identificativo dell'utente che ha creato il torneo.
     *
     * @return l'identificativo del creatore, mai {@code null}
     */
    public UserId getCreatedBy() {
        return createdBy;
    }

    /**
     * Restituisce l'istante di creazione del torneo.
     *
     * @return l'istante di creazione, mai {@code null}
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Confronta questo torneo con un altro oggetto verificandone l'uguaglianza
     * sulla base dell'identificativo del torneo.
     *
     * @param o oggetto da confrontare; può essere {@code null}
     * @return {@code true} se l'oggetto è un {@code Tournament} con lo stesso identificativo, {@code false} altrimenti
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tournament that = (Tournament) o;
        return Objects.equals(tournamentId, that.tournamentId);
    }

    /**
     * Restituisce il codice hash calcolato sull'identificativo del torneo.
     *
     * @return il codice hash del torneo
     */
    @Override
    public int hashCode() {
        return Objects.hash(tournamentId);
    }
}
