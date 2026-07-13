La short answer: il sistema attuale ragiona già con la chiave `(buildingId, gameId, sessionId)` + `UserId`, quindi l'associazione "Mario e Gianni sullo stesso calc-balilla" **esiste già a livello di modello**. Quello che cambia nel deploy IoT reale è la **fonte autoritativa** di quegli identificativi: l'emulatore li fa derivare dal form di login e dalla lista giochi pescata via REST; sul tavolo reale devono derivare dalla **presenza fisica** dei giocatori.

## Cosa identifica già oggi chi gioca su cosa

- `BuildingId`: identificativo dell'edificio (`app.building-id` nel `local-server`).
- `GameId`: identificativo della singola macchina (record nella tabella `game_catalog`, con `gameType=FOOSBALL`). È univoco **all'interno del building**.
- `SessionId`: identificativo della partita in corso (creato da `createLobby`, stato `WAITING`).
- `UserId`: identificativo del giocatore (dal lookup utente del sistema centrale).

Quando Mario e Gianni joinano la stessa `sessionId` con il loro `UserId`, il server li considera "nella stessa partita su quel calc-balilla". Non c'è nulla da aggiungere al dominio; c'è da stabilire **chi emette** i messaggi `lobby/create` e `lobby/join` e **come risolve** gli `UserId` dei due giocatori fisici.

## Il tavolo fa da client MQTT, badge RFID/NFC per gli utenti
Questa è la più pulita e quella che elimina app/scan/manual.

- Il tavolo ha un **edge controller** (RaspberryPi/ESP32) con dentro:
    - un `GameId` statico (URSA memorizzato in file di config / OTP bruciato in fabbrica nel firmware);
    - il `BuildingId` del datacenter a cui è associato;
    - le credenziali mTLS per il broker MQTT;
    - un lettore RFID/NFC sul tablet laterale.
- Mario appoggia il badge → l'edge controller risolve `cardUID → UserId` (lookup via central-system o local-server). Se Gianni non ha badge, la partita resta in attesa di `join`.
- Mario preme "Inizia" sul pannello del tavolo → il tavolo pubblica `building/.../game/{gameId}/session/lobby/create` con `creatorId=mario`. La `sessionId` torna sul tavolo via MQTT `session/start`.
- Gianni appoggia il badge → il tavolo pubblica `.../session/lobby/join` con `sessionId` (che già conosce) e `userId=gianni`.
- Il `GameSessionService.joinLobby` aggiunge Gianni alla stessa sessione. Da lì, due partecipanti sullo stesso `gameId` === "stesso calc-balilla".

Questa strategia fa del tavolo l'**affermante autoritativo** della presenza fisica: non serve che la app sappia nulla, e nessuna entità esterna può creare false partite su quel tavolo senza essere fisicamente lì.

## Quale manca al sistema attuale per il deploy reale

| Pezzo                                              | Stato attuale                                           | Cosa serve sul reale                                                                              |
|----------------------------------------------------|---------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| Identificativi del tavolo (`GameId`, `BuildingId`) | Esistono nel dominio e DB                               | Provisioning firmware: ogni tavolo deve avere `GameId+BuildingId` bruciati in factory/data config |
| Credenziali MQTT lato client                       | mTLS sul `local-server` già; per gli utenti connessi no | mTLS anche per i tavoli (CIAM originale per tavolo inserito nel truststore locale)                |
| Rendimento di `UserId` per player2                 | L'emulatore lo ricava dal form di login                 | Badge RFID (A), QR+app (B), BLE+app (C)                                                           |
| Affirmatore della presenza fisica                  | L'utente che seleziona il gioco via app                 | Il tavolo stesso (A) o il QR locale (B/C)                                                         |
| Validazione anti-spoof                             | Assente (flusso REST/MQTT aperto)                       | Rest GET accetta il `join` solo se accompagnato da sessionToken di prossimità scadenzabile        |

## Schema finale

```
[Tavolo C1, GameId=g1]                  [local-server]              [central-system]
┌──────────────────────────┐
│ edge controller          │
│ - mTLS mqtt client       │
│ - reader RFID            │
│ - GameId=g1 (fisso)      │
│                          │
│ Mario badge → UserId m.  │ ─ publish lobby/create ─→  createLobby(g1, m)
│                          │                                  sessione s1 WAITING
│ Gianni badge → UserId g. │ ←── MQTT session/start (sessionId s1) ──
│                          │                                  game.status=LOBBY
│                          │ ─ publish lobby/join ──→   joinLobby(s1, g)
│                          │                                  sessione s1 ha [m, g]
│ Mario preme "Avvia"      │
│                          │ ─ publish lobby/start ──→  startLobby(s1)
│                          │                                  sessione s1 IN_PROGRESS
│                          │                                  game.status=IN_USE
```

Il "dove due giocatori sono dichiarati sullo stesso tavolo" coincide con la lista `participants` di `GameSession`: nel momento in cui due `joinLobby` (o un `createLobby` + un `joinLobby`) alimentano la stessa `sessionId` per lo stesso `gameId`, il server ha già dichiarato l'associazione.

**In sintesi**: il modello concettuale è già corretto; nel mondo reale il tavolo stesso (con un suo ID fisato e un lettore utenti) prende il posto dell'emulatore, e la identità di ciascun giocatore emerge dal mondo fisico (badge/QR/BLE) anziché dal form di login dell'emulatore. La sessione flower per il vincolo è il medesimo — `lobby/create`→`lobby/join`→`lobby/start` — con i due player rispettivamente `creatorId` e `userId` dello stesso `gameId`.