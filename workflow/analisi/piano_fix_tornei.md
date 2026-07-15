# Piano di Fix Permanente — Tornei Semanticamente Corretti

> SOLO ANALISI + PIANO. NESSUNA MODIFICA AL CODICE in questo documento.
> Tutti i `file:riga` sono verificati sul repository al momento della stesura.

Requisiti imprescindibili:
- **R1** rifiutare tornei di giochi single-player-per-device (`SLOT_MACHINE`).
- **R2** permettere tornei multiplayer-per-device (CHESS 2-2, FOOSBALL/DARTS 2-4, MONOPOLY/RISK 2-6, ROULETTE 1-20).
- **R3** permettere tornei cross-building; ogni "match 1v1" del bracket `SINGLE_ELIMINATION` può opporre 2 partecipanti in edifici diversi.

---

## 1. Capacity + seed per GameType (tabella)

Query equivalente su `local_db` (i container MySQL locali sono inizializzati da `infrastructure/mysql-local/init*.sql`):

| GameType      | min | max | team_allowed | Seed central (`mysql-central/init.sql`) | machine building-1 | machine building-2 | machine building-3 |
|---------------|-----|-----|--------------|------------------------------------------|----------------------|----------------------|----------------------|
| CHESS         | 2   | 2   | FALSE        | init.sql:128                             | game-chess-1 (init.sql:126) | game-chess-2 (init-building-2.sql:126) | game-chess-3 (init-building-3.sql:126) |
| FOOSBALL      | 2   | 4   | TRUE         | init.sql:129                             | game-foosball-1 (init.sql:127) | game-foosball-2 (init-building-2.sql:127) | game-foosball-3 (init-building-3.sql:127) |
| DARTS         | 1   | 4   | TRUE         | init.sql:130                             | game-darts-1 (init.sql:128) | game-darts-2 (init-building-2.sql:128) | game-darts-3 (init-building-3.sql:128) |
| MONOPOLY      | 2   | 6   | TRUE         | init.sql:131                             | ASSENTE              | ASSENTE              | ASSENTE              |
| RISK          | 2   | 6   | TRUE         | init.sql:132                             | ASSENTE              | ASSENTE              | ASSENTE              |
| SLOT_MACHINE  | 1   | 1   | FALSE        | init.sql:133                             | game-slot-1 (init.sql:129) | game-slot-2 (init-building-2.sql:129) | game-slot-3 (init-building-3.sql:129) |
| ROULETTE      | 1   | 20  | TRUE         | init.sql:134                             | ASSENTE              | ASSENTE              | ASSENTE              |

Conseguenze (per F3 — seed game machine):
- `MONOPOLY`, `RISK`, `ROULETTE` hanno `game_definitions_local` (init.sql:176-184) ma NESSUNA machine in `game_catalog` di alcun building. La creazione del torneo DRAFT va a buon fine (il Central `TournamentService.create:106-108` vede il `game_definitions` presente), ma al primo `startMatch` il `gameRepository.findAll()...filter(AVAILABLE).findFirst()` ritorna null e parte `IllegalArgumentException` con messaggio "No AVAILABLE game machine for X in building Y..." (`PlayerTournamentController.java:159-163`).
- Ogni building ha SOLO 1 machine per ciascuno dei 4 tipi CHESS/FOOSBALL/DARTS/SLOT_MACHINE. La "parallelizzazione N match FOOSBALL cross-building" dell'utente richiede piu' machine FOOSBALL per building (F3 add rows a `game_catalog`).

Verifica formale (no SQL eseguito; il seed del file equivale al risultato):

```
SELECT game_type, name, min_players, max_players, team_allowed FROM game_definitions_local ORDER BY game_type;
-- CHESS/Scacchi 2/2/FALSE, DARTS/Freccette 1/4/TRUE, FOOSBALL/Calciobalilla 2/4/TRUE,
-- MONOPOLY/Monopoli 2/6/TRUE, RISK/Rischio 2/6/TRUE, ROULETTE/Roulette 1/20/TRUE,
-- SLOT_MACHINE/Slot Machine 1/1/FALSE

SELECT building_id, id, game_type, status FROM game_catalog ORDER BY building_id, game_type;
-- 3 building x 4 machine (CHESS, FOOSBALL, DARTS, SLOT_MACHINE) = 12 righe totali.
-- NESSUNA machine per MONOPOLY/RISK/ROULETTE.
```

---

## 2. Dinamiche per family (per ognuno dei 3 requisiti)

### Dinamiche (3 modalita')

- **SHARED-2-PLAYER** — un match del bracket = una `GameSession` unica su UNA machine di UNA building (l'"owner"); 2 partecipanti "vedono" la stessa sessione (il 2° si "unisce" via MQTT cross-building sul broker unico Mosquitto). Per giochi a stato condiviso / info-completa: CHESS, MONOPOLY, RISK. Avanzamento: `advanceWinner(matchId, winnerId)` con winner risolto dalla sessione.
- **SCORE_COMPARISON** — un match del bracket = due `GameSession` separate (una per partecipante, sulla machine del proprio building); ognuno submitta uno `score` numerico; il Central accumula entrambi i punteggi in `tournament_match_scores` e quando entrambi sono presenti chiama `advanceWinner(matchId, maxScoreWinner)`. Per giochi "skill-on-score" o "combo-driven": FOOSBALL, DARTS, ROULETTE. Mantiene i singoli `GAME_SESSION_COMPLETED` per player statistics.
- **RIFIUTATO** — torneo non consentito alla creazione. Per SLOT_MACHINE (e qualunque futuro GameType con `max_players == 1`).

### Tabella GameType x dinamica x requisito

| GameType | R1 (single-player-per-device) | R2 (multiplayer-per-device) | R3 (cross-building) | DINAMICA finale |
|----------|-------------------------------|-----------------------------|---------------------|------------------|
| CHESS | non single-player | OK (2-player same device) | SHARED cross-building (broker unico MQTT, turn sync tra 2 client) | **SHARED** |
| FOOSBALL | non single-player | OK (2-4 same table) | SCORE_COMPARISON (run + confronto gol tra edifici) | **SCORE_COMPARISON** (interpretazione A §2.1) |
| DARTS | non single-player | OK (1-4 stesso tavolo, turn-based) | SCORE_COMPARISON (run + confronto punti) | **SCORE_COMPARISON** |
| MONOPOLY | non single-player | OK (2-6 stesso tavolo) | SHARED cross-building (come CHESS) | **SHARED** |
| RISK | non single-player | OK (2-6 stesso tavolo) | SHARED cross-building (come CHESS) | **SHARED** |
| SLOT_MACHINE | RIFIUTATO alla create | rifiutato | rifiutato | **RIFIUTATO** |
| ROULETTE | non single-player | OK (1-20 tavolo combo) | SCORE_COMPARISON (run + confronto balance finale) | **SCORE_COMPARISON** |

### 2.1 Interpretazione scelta per FOOSBALL (e trade-off)

**Interpretazione A (scelta)**: il match "1v1" del bracket = ogni `participantX` fa una **single-player run** su una machine FOOSBALL del proprio building; lo `score` = gol che il partecipante segna (il pannello attuale `FoosballPanel.java:60-68, 96-119` ha gia' il bottone "GOAL Team1"; la run single-player conta `score1`); il vincitore del bracket = `max(score)` tra i 2 partecipanti sui vari edifici. Cross-building enabled.

- PRO: semplice, cross-building friendly, realistica per tornei online; corrisponde al testo utente "si fa 2 a 2... si mettono a confronto i punteggi tra i vari giocatori dei vari building".
- CONTRO: perde la fisicita' "2v2 nello stesso tavolo"; se i 2 partecipanti sono nello STESSO building, servono 2 machine foosball distinte (per seed attuale: 1 sola; i run sono serializzati sulla stessa machine). Per partecipanti 3+ (FOOSBALL 2-4 con `teamSize > 2`) si generalizza confrontando lo score-migliore-della-squadra.

**Interpretazione B (NON scelta)**: il match "vero 2v2" su stesso foosball fisico del building-owner; PERDE il cross-building (impossibile condividere tavolo tra edifici). Violerebbe R3.

### 2.2 Dinamica per CHESS (SHARED cross-building)

1. MATCH `participantA x participantB` = unica `GameSession` 2-player su una machine CHESS del building-owner (round-robin via `buildingIds`, come gia' `UserReplicationSchedulerService.java:1045-1047`).
2. PLAYER A (in building-owner) chiama `POST /api/players/tournaments/matches/{matchId}/start` (`PlayerTournamentController.java:131-187`) con `participants=[A,B]`.
3. PLAYER B (in altra building) fa **JOIN** via nuovo endpoint `POST /api/players/tournaments/matches/{matchId}/join` sulla propria Local → la Local forwarda HTTP alla Local-owner (gateway HTTP via `registered_local_servers_local`) → ritorna `GameSessionDto` con `gameId`+`buildingId` reali → il client B apre GamePlayView in join e subscribes MQTT `building/{ownerBuildingId}/game/{realGameId}/session/+` (broker unico cross-building).
4. Turn sync via MQTT (topic `.../session/turn`, `.../session/move`). `ChessPanel.java:273-285` e' gia' pronto (`broadcastTurn` + `movePublisher` + `onRemoteMove` + `onRemoteTurnUpdate`).
5. LATENZA: best-effort - l'UX degrada se latenza inter-building e' alta (vedi §11).

### 2.3 Dinamica per FOOSBALL/DARTS/ROULETTE (SCORE_COMPARISON cross-building)

1. MATCH replicato a TUTTE le Local dei building del torneo (patch §4.B-C2).
2. P-A chiama `POST .../matches/{id}/start` sulla propria Local con `participants=[A]` (size==1!). `GameSessionService.start:213-264` deve ACCETTERE size==1 in mode SCORE_COMPARISON (patch §4.B-L1).
3. P-B fa lo stesso contemporaneamente → 2 sessioni diverse su 2 machine potenzialmente in edifici diversi.
4. Ogni sessione termina con `GameResult.score=X`; `GameSessionService.end:482-561` emette outbox `TOURNAMENT_MATCH_COMPLETED` esteso con `(matchId, winner=null, participantId=A, score=gol_A, status="RUN_COMPLETED", mode="SCORE_COMPARISON")`.
5. Il Central `SyncEventProcessor.handleTournamentMatchCompleted:447-491` PATCH: se `mode==SCORE_COMPARISON` → INSERT in `tournament_match_scores(matchId, participantId, score, submittedAt)`; se COUNT per matchId == 2 → calcola `winner = max(score)`, salva `TournamentMatch` con `winner`+`status=COMPLETED`+`resultData` (JSON di entrambi gli scores), chiama `recomputeAfterCompletion(matchId)` + `advanceWinner(matchId, winner)` (signature invariata in `TournamentBracketService.java:287-365`). Se COUNT < 2 → mantieni match IN_PROGRESS (score parziale).
6. Niente MQTT turn sync (no stato condiviso), si riusa `session/end` solo per scatenare l'outbox.

### 2.4 Dinamica per MONOPOLY/RISK (SHARED, identica a CHESS)

Identica struttura di CHESS. I panel `MonopolyPanel.java:155-167` e `RiskPanel.java:188-201` gia' usano `TurnPublisher` (`broadcastTurn`) e `onRemoteTurnUpdate`. Nessuna patch ai panel; solo la patch del buildingId in `GamePlayView.java:514-515` (vedi §4.C-V1).

---

## 3. Architettura target

### 3.1 Schema logico

```
+-------------------+                  +---------------------------------+
|   Central (SoT)   |                  |   Local x N (read-only replica) |
| - tournaments     |  Outbox Pattern  |   - users, game_catalog          |
| - tournament_     |  drain ~5s       |   - tournaments_summary_local    |
|   matches         | <-------------- |   - tournament_matches_local     |
| - tournament_     |   REPLICATION    |   - tournament_participants_local|
|   match_scores    | -------------->  |   - team_members_local           |
|   (NUOVA)         |                  |   - game_definitions_local       |
| - game_definitions|                  | - writes (admin, start, join)   |
| - users           |                  |                                 |
+-------------------+                  +---------------------------------+
        ^                                            ^
        | outbox drain                               | REST (player, admin)
        | (Local COMPLETE_RUN/SHARED_WIN outbox)    | + MQTT (broker unico)
        v                                            v
+-------------------+                  +---------------------------------+
|  Central sync     |                  |  Game Client JavaFX x N          |
|  drain scheduler  |                  |  - parla con Local del proprio  |
|  ~5 s             |                  |    building (REST writes)       |
|  advanceWinner,   |                  |  - subscribe MQTT cross-building|
|  recomputeAfter   |                  |    (broker unico, canale MQTT    |
|  Completion,      |                  |    non dipende dalla Local)     |
|  replicate match  |                  +---------------------------------+
|  to ALL tournament|                                 
|  buildings (NON round-robin single)                 
+-------------------+                                 
```

### 3.2 Come funziona un match torneo (per dinamica)

**SHARED-2-PLAYER (CHESS, MONOPOLY, RISK)**:
1. Bracket schedule (`TournamentBracketService.java:185-201`) crea match con participantA/B → `TournamentMatchOutboxAdapter.publishScheduled` (`TournamentMatchOutboxAdapter.java:56-84`) emette `TOURNAMENT_MATCH_SCHEDULED` (NO buildingId, NO gameId ancora).
2. Drain Central→Local (`UserReplicationSchedulerService.replicateTournamentMatchEvent:1020-1139`) assegna UN `buildingId` (round-robin per bracketPosition) + `gameId` (PATCH §4.C2: usare machine reale del building-owner invece di UUID random) E REPLICA il match a TUTTE le Local dei building del torneo (NON solo all'owner).
3. PLAYER A (in building-owner) vede match in "My matches" (`PlayerTournamentController.java:77-124`); chiama POST `.../matches/{id}/start` → `GameSessionService.start(... participants=[A,B], tournamentMatchId)` (`GameSessionService.java:213-401`) con size==2 → `GameSession IN_PROGRESS` su `game-chess-X` del building-owner.
4. PLAYER B (in altra building) vede match REPLICATO in "My matches"; chiama POST `.../matches/{id}/join` (NUOVO endpoint) sulla propria Local → forward HTTP alla Local-owner → ottiene `GameSessionDto` del match IN_PROGRESS → apre GamePlayView in join con `setGameState(dto)`; PATCH `GamePlayView.java:514-515` affinche' il topic MQTT usi `ownerBuildingId` (da `currentGameState.buildingId()`) NON `this.buildingId`.
5. Turn sync via MQTT (broker unico). `ChessPanel.broadcastTurn`/`broadcastMove` gia' pronti.
6. End: P-A chiama `POST /api/sessions/{sessionId}/end` con `GameResult.winnerId = A o B`. `GameSessionService.end:404-578` emette outbox `GAME_SESSION_COMPLETED` + `TOURNAMENT_MATCH_COMPLETED` SHARED con `(matchId, winner, status="COMPLETED", mode="SHARED")`.
7. Drain Local→Central: `SyncEventProcessor.handleTournamentMatchCompleted:447-491` ricostruisce match con `winner`, salva, `recomputeAfterCompletion` + `advanceWinner(matchId, winner)`. Avanza bracket.

**SCORE_COMPARISON (FOOSBALL, DARTS, ROULETTE)**:
1. Passi 1-2 identici al SHARED (replica a tutti i building del torneo).
2. PLAYER A (in building-A) vede match; chiama POST `.../matches/{id}/start` con `participants = [A]` (size==1). `GameSessionService.start:213-264` accetta size==1 in mode SCORE_COMPARISON (PATCH §4.B-L1).
3. PLAYER B (in building-B) fa lo stesso in parallelo → 2 sessioni separate, su 2 machine potenzialmente in edifici diversi.
4. Ogni sessione marca end con `GameResult.score = lokalScore`. `GameSessionService.end:482-573` PATCH: emette outbox `TOURNAMENT_MATCH_COMPLETED` SCORE_COMPARISON con `(matchId, winner=null, participantId=A, score=gol_A, status="RUN_COMPLETED", mode="SCORE_COMPARISON")`.
5. Central `SyncEventProcessor.handleTournamentMatchCompleted` PATCH: SCORE_COMPARISON → INSERT in `tournament_match_scores(matchId, participantId, score, submittedAt)` (PK `(matchId, participantId)` per idempotenza su re-drain); se COUNT==2 → winner=max(score), salva match.status=COMPLETED, `recomputeAfterCompletion` + `advanceWinner(matchId, winner)`; se COUNT<2 → mantieni IN_PROGRESS (score parziale).
6. Idempotenza su ri-drain garantita dalla PK su `(matchId, participantId)` (pattern gia' usato per `replication_progress` in `UserReplicationSchedulerService.java:1124-1127`).

### 3.3 advanceWinner per entrambe le dinamiche

`TournamentBracketService.advanceWinner(matchId, winnerId)` (`TournamentBracketService.java:287-365`) resta INVARIATO in signature. La differenza e' chi lo chiama:
- SHARED: `SyncEventProcessor.handleTournamentMatchCompleted` lo chiama direttamente con `dto.winner()` (come gia' in `SyncEventProcessor.java:477-478`).
- SCORE_COMPARISON: lo chiama solo dopo l'accumulo di 2 record in `tournament_match_scores`, con `winnerId = max(score)` calcolato lato Central.

### 3.4 Replicare match cross-building (PATCH critica)

`UserReplicationSchedulerService.replicateTournamentMatchEvent:1076-1084`: sostituire il singolo `targetServers` filtrato per `assignedBuildingId` con la lista di TUTTE le Local attive i cui `buildingId` ∈ `tournamentBuildingRepository.findByTournament(tournamentId)`.

Coerentemente, anche `LateRegistrationCatchUpService.java:501-548` (linea 521 `if (matchBuildingId == null || !matchBuildingId.equals(serverId)) skip` diventa "push se `tournamentBuildings.contains(serverId)`").

Il `buildingId`+`gameId` sono ancora assegnati alla singola Local "owner" (per la machine CHESS reale del match SHARED, o per la machine OWNER di SCORE_COMPARISON), ma la replica del `TournamentMatchLocal` viaggia a tutte le Local del torneo. L'owner resta nel `TournamentMatchScheduledDto.buildingId` cosicche' ogni Local sappia chi e' l'owner per il forwarding JOIN.

### 3.5 Come apre la GamePlayView il client - per dinamica

- SHARED JOIN: il 2° client chiama `POST /api/players/tournaments/matches/{matchId}/join` → ritorna `GameSessionDto` (gameId reale + owner buildingId, sessionId, gameType, participants=[A,B]) → `MainView` naviga a GamePlayView con `setGameState(dto); setMqttContext(adapter, dto.buildingId()); setEffectiveGameId(dto.gameId())` → lancia il panel corretto (chess/monopoly/risk) e `wireTurnSynchronization` subscribe al topic dell'owner. PATCH `GamePlayView.java:514-515`: building-id del topic MQTT = `currentGameState.buildingId()` (owner), NON `this.buildingId` (pattern gia' usato in `LobbyView.java:228-231`).
- SCORE_COMPARISON: ogni client chiama l'endpoint `start` esistente (`PlayerTournamentController.java:131-187`); riceve `GameSessionDto` della propria run single-player; NON si subscribe ad alcun topic; a end-event il pannello FOOSBALL/DARTS/ROULETTE estrae lo score dal proprio stato.

<!-- CHUNK_END -->