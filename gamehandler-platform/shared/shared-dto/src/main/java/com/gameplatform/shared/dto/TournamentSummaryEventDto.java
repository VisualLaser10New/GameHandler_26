package com.gameplatform.shared.dto;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentStatus;
import java.time.Instant;
import java.util.List;

/**
 * Payload dell'outbox per l'evento {@code TOURNAMENT_SUMMARY_UPSERTED} nel
 * flusso di replica Central&rarr;Local (caso d'uso &sect;7.A.1). Trasporta un
 * riepilogo appiattito del torneo affinch&eacute; il nodo locale possa
 * inserire o aggiornare la propria proiezione.
 *
 * <p>Il valore {@code deleted == true} indica un tombstone: il nodo locale
 * deve eliminare la propria proiezione per l'identificativo {@code tournamentId}
 * invece di eseguirne l'upsert. Il campo {@code originatingRequestId} &egrave;
 * nullable: {@code null} per gli eventi generati dal ramo REST diretto e non
 * nullo per il ramo SyncEventProcessor (&sect;7.A.3), dove trasporta l'id
 * dell'evento outbox originario per la tracciatura dell'idempotenza.</p>
 *
 * <p>Il campo {@code errorMessage} &egrave; non nullo quando il caso d'uso
 * Central che ha gestito un evento {@code *_REQUESTED} ha respinto la
 * richiesta (ad esempio una transizione del ciclo di vita di un torneo rifiutata
 * perch&eacute; lo {@code TournamentStatus} corrente non la ammette, oppure il
 * torneo non &egrave; stato trovato). In tal caso il
 * {@code TournamentSummarySyncService} locale chiude la riga corrispondente di
 * {@code admin_requests_local} come {@code FAILED} con la motivazione leggibile
 * in {@code result_data}, anzich&eacute; come {@code COMPLETED}, chiudendo il
 * ciclo immediatamente invece di attendere che la scadenza
 * {@code admin.request.timeout-ms} scatti dopo 30 minuti e riporti un vago
 * "TIMEOUT" all'amministratore di piattaforma (BUG-CANCEL-PENDING).</p>
 *
 * @param eventId              identificativo dell'evento outbox (UUID)
 * @param eventType            sempre {@code TOURNAMENT_SUMMARY_UPSERTED}
 * @param tournamentId         l'identificativo del torneo
 * @param name                 il nome del torneo
 * @param gameType             il tipo di gioco
 * @param teamBased            indica se il torneo &egrave; a squadre
 * @param teamSize             dimensione della squadra (1 per individuale)
 * @param status               lo stato del torneo
 * @param startsAt             istante di avvio pianificato
 * @param endsAt               istante di fine effettivo (null finch&eacute; non COMPLETED)
 * @param buildingIds          gli edifici che ospitano il torneo
 * @param participantsCount    numero di partecipanti iscritti
 * @param updatedAt            istante dell'ultima mutazione
 * @param deleted              {@code true} per un evento tombstone
 * @param originatingRequestId id della richiesta/evento originario (nullable)
 * @param errorMessage         motivazione leggibile del rifiuto (nullable; non nullo &rArr;
 *                             il locale deve marcare la richiesta admin
 *                             corrispondente come {@code FAILED} anzich&eacute; {@code COMPLETED})
 *
 * @see com.gameplatform.shared.domain.model.GameType
 * @see com.gameplatform.shared.domain.model.TournamentStatus
 */
public record TournamentSummaryEventDto(
        String eventId,
        String eventType,
        String tournamentId,
        String name,
        GameType gameType,
        boolean teamBased,
        int teamSize,
        TournamentStatus status,
        Instant startsAt,
        Instant endsAt,
        List<String> buildingIds,
        int participantsCount,
        Instant updatedAt,
        boolean deleted,
        String originatingRequestId,
        String errorMessage
) {
    /**
     * Costruttore secondario di retrocompatibilit&agrave; (senza indicazione di
     * fallimento): delega al costruttore canonico a 16 argomenti impostando
     * {@code errorMessage = null}. I siti di chiamata esistenti (flusso
     * FASE 7.A di creazione/apertura/aggiornamento/eliminazione, rami di
     * successo delle richieste admin e test) conservano la precedente arit&agrave;
     * a 15 argomenti invariata.
     *
     * @param eventId              identificativo dell'evento outbox (UUID)
     * @param eventType            sempre {@code TOURNAMENT_SUMMARY_UPSERTED}
     * @param tournamentId         l'identificativo del torneo
     * @param name                 il nome del torneo
     * @param gameType             il tipo di gioco
     * @param teamBased            indica se il torneo &egrave; a squadre
     * @param teamSize             dimensione della squadra (1 per individuale)
     * @param status               lo stato del torneo
     * @param startsAt             istante di avvio pianificato
     * @param endsAt               istante di fine effettivo (null finch&eacute; non COMPLETED)
     * @param buildingIds          gli edifici che ospitano il torneo
     * @param participantsCount    numero di partecipanti iscritti
     * @param updatedAt            istante dell'ultima mutazione
     * @param deleted              {@code true} per un evento tombstone
     * @param originatingRequestId id della richiesta/evento originario (nullable)
     */
    public TournamentSummaryEventDto(String eventId, String eventType, String tournamentId, String name,
                                     GameType gameType, boolean teamBased, int teamSize, TournamentStatus status,
                                     Instant startsAt, Instant endsAt, List<String> buildingIds,
                                     int participantsCount, Instant updatedAt, boolean deleted,
                                     String originatingRequestId) {
        this(eventId, eventType, tournamentId, name, gameType, teamBased, teamSize, status, startsAt, endsAt,
                buildingIds, participantsCount, updatedAt, deleted, originatingRequestId, null);
    }

    /**
     * Costruttore secondario di retrocompatibilit&agrave; legacy a 14 argomenti,
     * privo di tombstone, {@code originatingRequestId} ed {@code errorMessage}.
     * Delega al costruttore canonico impostando {@code deleted = false},
     * {@code originatingRequestId = null} ed {@code errorMessage = null}, cos&igrave;
     * da preservare i siti di chiamata pi&ugrave; datati.
     *
     * @param eventId           identificativo dell'evento outbox (UUID)
     * @param eventType         sempre {@code TOURNAMENT_SUMMARY_UPSERTED}
     * @param tournamentId      l'identificativo del torneo
     * @param name              il nome del torneo
     * @param gameType          il tipo di gioco
     * @param teamBased         indica se il torneo &egrave; a squadre
     * @param teamSize          dimensione della squadra (1 per individuale)
     * @param status            lo stato del torneo
     * @param startsAt          istante di avvio pianificato
     * @param endsAt            istante di fine effettivo (null finch&eacute; non COMPLETED)
     * @param buildingIds       gli edifici che ospitano il torneo
     * @param participantsCount numero di partecipanti iscritti
     * @param updatedAt         istante dell'ultima mutazione
     */
    public TournamentSummaryEventDto(String eventId, String eventType, String tournamentId, String name,
                                     GameType gameType, boolean teamBased, int teamSize, TournamentStatus status,
                                     Instant startsAt, Instant endsAt, List<String> buildingIds,
                                     int participantsCount, Instant updatedAt) {
        this(eventId, eventType, tournamentId, name, gameType, teamBased, teamSize, status, startsAt, endsAt,
                buildingIds, participantsCount, updatedAt, false, null, null);
    }
}