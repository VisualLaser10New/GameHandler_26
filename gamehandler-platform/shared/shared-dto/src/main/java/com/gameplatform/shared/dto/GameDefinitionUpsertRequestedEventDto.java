package com.gameplatform.shared.dto;

import com.gameplatform.shared.domain.model.GameType;

import java.time.Instant;
import java.util.Map;

/**
 * Payload dell'outbox per l'evento {@code GAME_DEFINITION_UPSERT_REQUESTED},
 * emesso da un caso d'uso GAME_ADMIN del Local Server e consumato dal
 * {@code SyncEventProcessor} del Central, che delega all'uso di caso
 * {@code UpsertGameDefinitionUseCase} per l'inserimento o l'aggiornamento della
 * definizione di gioco.
 *
 * <p>Il {@code requestId} coincide con l'{@code eventId} dell'outbox del Local;
 * l'evento di ritorno del Central ({@code GAME_DEFINITION_UPSERTED}) lo riporta
 * come {@code originatingRequestId} così il Local può completare la richiesta.</p>
 *
 * @param eventId            identificativo dell'evento outbox del Local (UUID); non deve essere {@code null} né vuoto
 * @param eventType          tipo dell'evento, sempre {@code GAME_DEFINITION_UPSERT_REQUESTED}; non deve essere {@code null} né vuoto
 * @param requestId          identificativo della richiesta admin, uguale a {@code eventId}; non deve essere {@code null} né vuoto
 * @param actingUserId       identificativo dell'utente admin (GAME_ADMIN) che richiede la modifica; non deve essere {@code null} né vuoto
 * @param actingRole         ruolo dell'admin che effettua l'operazione; non deve essere {@code null} né vuoto
 * @param buildingId         identificativo dell'edificio in cui l'admin è connesso; non deve essere {@code null} né vuoto
 * @param gameType           tipo di gioco da inserire o aggiornare; non deve essere {@code null}
 * @param name               nome visualizzato del gioco; non deve essere {@code null} né vuoto
 * @param minPlayers         numero minimo di giocatori; deve essere strettamente positivo (>= 1)
 * @param maxPlayers         numero massimo di giocatori; deve essere >= {@code minPlayers}
 * @param teamAllowed        indica se è consentito il gioco in modalità a squadre
 * @param registrationRules  mappa delle regole di registrazione; non deve essere {@code null}, può essere vuota
 * @param createdAt          istante di creazione della richiesta; non deve essere {@code null}
 *
 * @see com.gameplatform.shared.dto.GameDefinitionUpsertedEventDto
 */
public record GameDefinitionUpsertRequestedEventDto(
        String eventId,
        String eventType,
        String requestId,
        String actingUserId,
        String actingRole,
        String buildingId,
        GameType gameType,
        String name,
        int minPlayers,
        int maxPlayers,
        boolean teamAllowed,
        Map<String, Object> registrationRules,
        Instant createdAt
) {
}