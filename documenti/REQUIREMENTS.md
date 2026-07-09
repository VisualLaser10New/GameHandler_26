# REQUIREMENTS.md — GameHandler_26: Boardgame Platform

> **Documento:** Requisiti di Sistema
> **Versione:** 1.0
> **Data:** 2026-06-29
> **Stato:** Bozza in revisione
> **Pubblico:** Development Team, QA, Analyst
>
> **Legenda stato implementazione:**
> - ✅ **Implementato e documentato** — il codice esiste e questo documento lo descrive correttamente
> - 🔶 **Implementato ma non documentato** — il codice esiste ma mancava documentazione formale
> - 📋 **Documentato ma non implementato** — requisito pianificato, non ancora in codice
> - ⚠️ **Parzialmente implementato** — implementazione incompleta o con known issues

---

## Indice

1. [Requisiti Funzionali (RF)](#1-requisiti-funzionali-rf)
   - 1.1 [Modulo: Autenticazione e Utenti](#11-modulo-autenticazione-e-utenti)
   - 1.2 [Modulo: Prenotazioni](#12-modulo-prenotazioni)
   - 1.3 [Modulo: Sessioni di Gioco](#13-modulo-sessioni-di-gioco)
   - 1.4 [Modulo: Stato Dispositivi](#14-modulo-stato-dispositivi)
   - 1.5 [Modulo: Statistiche](#15-modulo-statistiche)
   - 1.6 [Modulo: Sincronizzazione Central ↔ Local](#16-modulo-sincronizzazione-central--local)
   - 1.7 [Modulo: Sicurezza e PKI](#17-modulo-sicurezza-e-pki)
   - 1.8 [Modulo: Resilienza e Recovery](#18-modulo-resilienza-e-recovery)
2. [Requisiti Non Funzionali (RNF)](#2-requisiti-non-funzionali-rnf)
3. [Requisiti di Integrazione](#3-requisiti-di-integrazione)
4. [Requisiti di Dati](#4-requisiti-di-dati)
5. [Requisiti di Infrastruttura](#5-requisiti-di-infrastruttura)
6. [Matrice di Tracciabilità](#6-matrice-di-tracciabilità)

---

## 1. Requisiti Funzionali (RF)

### Priorità MoSCoW

| Simbolo | Significato              |
|---------|--------------------------|
| **M**   | Must Have                |
| **S**   | Should Have              |
| **C**   | Could Have               |
| **W**   | Won't Have (this release)|

---

### 1.1 Modulo: Autenticazione e Utenti

#### RF-AU-01 — Registrazione Utente (Central System)
- **Priorità:** M
- **Stato:** ✅ Implementato e documentato
- **Descrizione:** Un visitatore non autenticato può registrarsi alla piattaforma fornendo username, email e password. Il sistema verifica l'unicità dello username e salva la password come hash BCrypt.
- **API:** `POST /api/users` (Central System, pubblico)
- **Fonte:** `[UserController.java]`, `[UserService.java]`, `[init.sql central — tabella users]`
- **Criteri di accettazione:**
  - La risposta è `201 Created` con `userId` generato (UUID v4).
  - Se lo username esiste già → `409 Conflict` (`UserAlreadyExistsException`).
  - La password non viene mai restituita in risposta; solo l'hash BCrypt è persistito.
  - L'evento `USER_REGISTERED` viene scritto nella tabella `outbox_events` del Central System per propagazione asincrona ai Local Server.

#### RF-AU-02 — Login Utente (Central System)
- **Priorità:** M
- **Stato:** ✅ Implementato e documentato
- **Descrizione:** Un utente registrato può autenticarsi sul Central System e ricevere un JWT (RS256, scadenza configurabile via `jwt.expiration-ms`, default 24 ore).
- **API:** `POST /api/auth/login` (Central System, pubblico)
- **Fonte:** `[AuthController.java]`, `[AuthService.java]`, `[JwtTokenProvider.java]`
- **Criteri di accettazione:**
  - Credenziali corrette → `200 OK` con `token` JWT.
  - Credenziali errate → `401 Unauthorized` (`InvalidCredentialsException`).
  - Il JWT contiene i claim `sub` (username), `userId`, `roles`, `iat`, `exp`.
  - Il token è firmato con la chiave RSA privata del Central System; non è valido su nessun Local Server.
  - Il sistema traccia i tentativi falliti nella tabella `failed_login_attempts`. [DA CHIARIRE: soglia di rate limiting e durata del blocco]

#### RF-AU-03 — Login Locale (Local Server, Offline-First)
- **Priorità:** M
- **Stato:** ✅ Implementato e documentato
- **Descrizione:** Un utente può autenticarsi su qualsiasi Local Server anche in assenza di connettività verso il Central System, grazie alla replica locale degli utenti nella tabella `replicated_users`.
- **API:** `POST /api/auth/login` (Local Server, pubblico)
- **Fonte:** `[LocalAuthService.java]`, `[init.sql local — tabella replicated_users]`
- **Criteri di accettazione:**
  - Il JWT emesso dal Local Server è firmato con la coppia RSA locale (diversa dal Central).
  - Il JWT del Central System non è accettato dal Local Server e viceversa.
  - Se l'utente non è nella tabella `replicated_users` → `401 Unauthorized`.
  - Il login funziona anche con il Central System irraggiungibile.

#### RF-AU-04 — Aggiornamento Utente
- **Priorità:** S
- **Stato:** ✅ Implementato e documentato
- **Descrizione:** Un amministratore (`ROLE_ADMIN`) può modificare i dati di un utente esistente sul Central System.
- **API:** `PUT /api/users/{id}` (Central System, richiede `ROLE_ADMIN`)
- **Fonte:** `[UserController.java]`, `[UserService.java]`
- **Criteri di accettazione:**
  - L'operazione è accessibile solo con JWT valido e ruolo `ROLE_ADMIN`.
  - L'aggiornamento genera un evento `USER_UPDATED` nell'outbox per propagazione ai Local Server.
  - Se l'utente non esiste → `404 Not Found`.

#### RF-AU-05 — RBAC (Role-Based Access Control)
- **Priorità:** M
- **Stato:** ✅ Implementato e documentato
- **Descrizione:** Il sistema implementa due ruoli: `ROLE_USER` (utente normale) e `ROLE_ADMIN` (amministratore). I ruoli sono codificati nel JWT e verificati da Spring Security.
- **Fonte:** `[SecurityConfig.java]`, `[JwtAuthenticationFilter.java]`
- **Criteri di accettazione:**
  - Le API `/internal/**` sono protette da API Key (`X-Internal-Api-Key`), non da JWT.
  - Le API `/api/statistics` del Central System richiedono `ROLE_ADMIN`.
  - Le API di prenotazione e sessione richiedono `ROLE_USER`.

---

### 1.2 Modulo: Prenotazioni

#### RF-PR-01 — Creazione Prenotazione
- **Priorità:** M
- **Stato:** ✅ Implementato e documentato (POF-5 risolto)
- **Descrizione:** Un utente autenticato può prenotare un tavolo da gioco specificando `gameId`, orario di inizio e orario di fine.
- **API:** `POST /api/reservations` (Local Server, richiede `ROLE_USER`)
- **Fonte:** `[ReservationService.java]`, `[init.sql local — tabella reservations]`
- **Criteri di accettazione:**
  - Il gioco deve essere in stato `AVAILABLE`; altrimenti → eccezione `GameNotAvailableException`.
  - La prenotazione non può essere creata con orario di fine nel passato (`ReservationExpiredException`).
  - Alla creazione, lo stato del gioco transisce da `AVAILABLE` a `RESERVED`.
  - La transizione viene pubblicata sul topic MQTT `building/{buildingId}/game/{gameId}/state` (QoS 1, Retained).
  - L'evento `RESERVATION_CREATED` viene scritto nell'outbox per sync con il Central System.
  - ✅ **POF-5 risolto:** `@Version` (ottimistic lock) su `GameJpaEntity` e `ReservationJpaEntity`; in caso di richieste concorrenti per lo stesso `gameId` il perdente ottiene `ConcurrentStateException` → 409 (REST) o ack-and-drop (MQTT). Race condition su prenotazione non più possibile.

#### RF-PR-02 — Cancellazione Prenotazione
- **Priorità:** M
- **Stato:** ✅ Implementato e documentato
- **Descrizione:** Il proprietario di una prenotazione può cancellarla, purché sia in stato `PENDING` e l'orario di inizio sia a più di 1 ora di distanza.
- **API:** `DELETE /api/reservations/{id}` (Local Server, richiede `ROLE_USER`)
- **Fonte:** `[ReservationService.java]`
- **Criteri di accettazione:**
  - Solo il proprietario della prenotazione può cancellarla (verifica `userId`).
  - Non si può cancellare una prenotazione già scaduta (`EXPIRED`).
  - Non si può cancellare con meno di 1 ora all'inizio.
  - Alla cancellazione il gioco torna in stato `AVAILABLE` e la transizione è pubblicata su MQTT.
  - L'evento `RESERVATION_CANCELLED` viene scritto nell'outbox.

#### RF-PR-03 — Lista Prenotazioni Utente
- **Priorità:** M
- **Stato:** ✅ Implementato e documentato
- **Descrizione:** Un utente autenticato può visualizzare le proprie prenotazioni sul Local Server.
- **API:** `GET /api/reservations` (Local Server, richiede `ROLE_USER`)
- **Fonte:** `[ReservationService.java]`, `[ReservationRepository]`

#### RF-PR-04 — Scadenza Automatica Prenotazioni
- **Priorità:** M
- **Stato:** ✅ Implementato e documentato
- **Descrizione:** Le prenotazioni scadute (il cui `end_time` è nel passato e sono in stato `PENDING`) vengono automaticamente marcate `EXPIRED` e il gioco torna disponibile.
- **Fonte:** `[ReservationExpirationService.java]` — `@Scheduled(fixedRate = 60000)`
- **Criteri di accettazione:**
  - Il job viene eseguito ogni 60 secondi.
  - Per ogni prenotazione scaduta: stato → `EXPIRED`, gioco → `AVAILABLE`, stato pubblicato su MQTT.
  - L'operazione è transazionale (`@Transactional`); la pubblicazione MQTT avviene dopo il commit.

---

### 1.3 Modulo: Sessioni di Gioco

#### RF-SE-01 — Avvio Sessione
- **Priorità:** M
- **Stato:** ✅ Implementato e documentato
- **Descrizione:** Un utente può avviare una sessione di gioco su un dispositivo, opzionalmente associandola a una prenotazione esistente.
- **API:** `POST /api/sessions/start` (Local Server, richiede `ROLE_USER`)
- **Fonte:** `[GameSessionService.java]`
- **Criteri di accettazione:**
  - Se è già attiva una sessione sullo stesso dispositivo → `SessionAlreadyActiveException`.
  - Se viene fornito un `reservationId`: la prenotazione deve essere `PENDING` e non scaduta, e il `gameId` deve corrispondere.
  - Se non viene fornito un `reservationId`: il gioco non deve essere in stato `RESERVED`.
  - Lo stato del gioco transisce a `IN_USE`; la transizione è pubblicata su MQTT.

#### RF-SE-02 — Termine Sessione
- **Priorità:** M
- **Stato:** ✅ Implementato e documentato
- **Descrizione:** Un utente può terminare una sessione attiva, registrando il risultato di gioco (polimorfismo via `@JsonTypeInfo`).
- **API:** `POST /api/sessions/{id}/end` (Local Server, richiede `ROLE_USER`)
- **Fonte:** `[GameSessionService.java]`, `[GameResult.java]` (shared-domain)
- **Criteri di accettazione:**
  - La sessione transisce in stato `COMPLETED`.
  - Il `GameResult` (che include `winner_id`, `win_condition`, `result_data` JSON) viene persistito.
  - Lo stato del gioco torna `AVAILABLE`.
  - L'evento `GAME_SESSION_COMPLETED` viene scritto nell'outbox.
  - Se la sessione era già in stato `ABORTED` (timeout heartbeat), il risultato viene comunque registrato (late arrival handling).

#### RF-SE-03 — Pausa e Ripresa Sessione
- **Priorità:** S
- **Stato:** ✅ Implementato e documentato
- **Descrizione:** Un utente può mettere in pausa e riprendere una sessione attiva.
- **API:** `POST /api/sessions/{id}/pause`, `POST /api/sessions/{id}/resume` (Local Server, richiede `ROLE_USER`)
- **Fonte:** `[GameSessionService.java]`
- **Criteri di accettazione:**
  - I topic MQTT `session/pause` e `session/resume` vengono pubblicati (QoS 1) dopo il commit della transazione.
  - Lo stato del dispositivo rimane `IN_USE` durante la pausa.

#### RF-SE-04 — Abort Automatico per Timeout Heartbeat
- **Priorità:** M
- **Stato:** ✅ Implementato e documentato
- **Descrizione:** Se un Game Client non risponde per 3 cicli consecutivi di health check (15 minuti), la sessione attiva viene automaticamente abortita.
- **Fonte:** `[HealthCheckService.java]` — `@Scheduled(fixedRate = 300000)`, threshold `missed >= 3`
- **Criteri di accettazione:**
  - Lo stop reason è `TIMEOUT`.
  - L'evento `GAME_SESSION_COMPLETED` (con stato `ABORTED`) viene scritto nell'outbox.
  - Il dispositivo torna in stato `AVAILABLE` solo se era `IN_USE`.
  - Un alert viene pubblicato sul topic `building/{buildingId}/alerts` (QoS 1).

---

### 1.4 Modulo: Stato Dispositivi

#### RF-GS-01 — Lista Giochi
- **Priorità:** M
- **Stato:** ✅ Implementato e documentato
- **Descrizione:** Un utente autenticato può ottenere la lista di tutti i dispositivi di gioco gestiti dal Local Server corrente.
- **API:** `GET /api/games` (Local Server, richiede `ROLE_USER`)
- **Fonte:** `[GameStateService.java]`

#### RF-GS-02 — Lista Giochi Disponibili
- **Priorità:** M
- **Stato:** ✅ Implementato e documentato
- **Descrizione:** Un utente può filtrare i giochi in stato `AVAILABLE`.
- **API:** `GET /api/games/available` (Local Server, richiede `ROLE_USER`)
- **Fonte:** `[GameStateService.java]`

#### RF-GS-03 — Aggiornamento Stato Real-Time via MQTT
- **Priorità:** M
- **Stato:** ✅ Implementato e documentato
- **Descrizione:** Ogni transizione di stato di un dispositivo (AVAILABLE → RESERVED → IN_USE → AVAILABLE) viene pubblicata sul broker MQTT locale con messaggio retained.
- **Topic:** `building/{buildingId}/game/{gameId}/state` (QoS 1, Retained)
- **Fonte:** `[PublishGameStatePort]`, `[GameStatePublisher.java]`, `[MqttTopics.java]` (shared-mqtt)

#### RF-GS-04 — Heartbeat Device
- **Priorità:** M
- **Stato:** ✅ Implementato e documentato
- **Descrizione:** Il Local Server invia un ping via MQTT a ogni dispositivo ogni 5 minuti; il dispositivo risponde su un topic dedicato. Il mancato riscontro per 3 cicli consecutivi attiva l'ABORT.
- **Topic ping:** `building/{buildingId}/game/{gameId}/heartbeat` (QoS 0)
- **Topic ack:** `building/{buildingId}/game/{gameId}/heartbeat/ack` (QoS 0)
- **Fonte:** `[HealthCheckService.java]`, `[HeartbeatService.java]` (client), `[HeartbeatPublisher.java]` (client)

---

### 1.5 Modulo: Statistiche

#### RF-ST-01 — Statistiche Locali
- **Priorità:** S
- **Stato:** ✅ Implementato e documentato
- **Descrizione:** Un utente autenticato può visualizzare le statistiche di utilizzo locali (per tipo di gioco) sul Local Server corrente.
- **API:** `GET /api/statistics` (Local Server, richiede `ROLE_USER`)
- **Fonte:** `[StatisticsService.java]`, `[LocalStatistics.java]`
- **Dati esposti:** totale sessioni, durata media (secondi), totale prenotazioni, sessioni attive in corso.

#### RF-ST-02 — Statistiche Globali Aggregate
- **Priorità:** S
- **Stato:** ✅ Implementato e documentato
- **Descrizione:** Un amministratore può visualizzare le statistiche aggregate globali (per edificio e tipo di gioco, per periodo) sul Central System.
- **API:** `GET /api/statistics` (Central System, richiede `ROLE_ADMIN`)
- **Fonte:** `[StatisticsController.java]`, `[StatisticsAggregationService.java]`, `[init.sql central — tabella aggregated_statistics]`
- **Dati esposti:** `total_sessions`, `avg_duration_seconds`, `total_reservations`, `period_start`/`period_end`.

---

### 1.6 Modulo: Sincronizzazione Central ↔ Local

#### RF-SY-01 — Sync Local → Central (Outbox Pattern)
- **Priorità:** M
- **Stato:** ✅ Implementato e documentato (POF-7 risolto; POF-3 risolto lato Local, residuo Central)
- **Descrizione:** Gli eventi locali (sessioni completate, prenotazioni create/cancellate) vengono accumulati nella tabella `outbox_events` del Local Server e inviati periodicamente al Central System.
- **Fonte:** `[SyncSchedulerService.java]` — `@Scheduled(fixedRate = 300000)`
- **Criteri di accettazione:**
  - Il sync avviene ogni 5 minuti (300 000 ms) o alla prima opportunità dopo una disconnessione.
  - Prima di inviare, viene verificata la raggiungibilità del Central System.
  - In caso di successo, gli eventi vengono marcati `SENT`; in caso di fallimento, viene incrementato il contatore `retry_count`.
  - ✅ **POF-3 risolto (Local):** `OutboxPurgeService` (purge SENT > `app.outbox-purge-retention-days`, default 7gg) + `OutboxDlqPromotionService` (FAILED → `outbox_dead_letter`). ⚠️ **Residuo Central:** la tabella `outbox_events` centrale SENT cresce ancora senza limite (nessun purge/DLQ centrale).
  - ✅ **POF-7 risolto:** lettura limitata via `findPendingLimit(batchSize)` (`app.outbox.batch-size`, default 50); isolamento del poison event per-event su fallimento del trasporto; `markAsSentBatch` atomico sul successo; promozione DLQ dopo 10 retry.

#### RF-SY-02 — Ricezione Sync (Central System)
- **Priorità:** M
- **Stato:** ✅ Implementato e documentato
- **Descrizione:** Il Central System espone un endpoint interno per ricevere i payload di sync dai Local Server e processarli in modo idempotente.
- **API:** `POST /internal/sync/receive` (Central System, richiede API Key)
- **Fonte:** `[SyncController.java]`, `[SyncReceiverService.java]`, `[init.sql central — tabella processed_events]`
- **Criteri di accettazione:**
  - L'idempotenza è garantita dalla tabella `processed_events`: eventi già processati vengono ignorati (`DuplicateEventException`).
  - La verifica avviene tramite `eventId` univoco.

#### RF-SY-03 — Replica Utenti Central → Local
- **Priorità:** M
- **Stato:** ✅ Implementato e documentato
- **Descrizione:** Il Central System propaga gli utenti registrati/aggiornati a tutti i Local Server attivi, in batch da al massimo 50 eventi per ciclo.
- **Fonte:** `[UserReplicationSchedulerService.java]` — `@Scheduled(fixedDelay = 300000)`, `BATCH_SIZE = 50`
- **Criteri di accettazione:**
  - Un evento è marcato `SENT` solo quando è stato propagato con successo a tutti i Local Server attivi.
  - Il fallimento su un singolo server non blocca la propagazione agli altri.
  - Il progresso di replica per-server è tracciato in `ReplicationProgress`.

#### RF-SY-04 — Registrazione Local Server
- **Priorità:** M
- **Stato:** ✅ Implementato e documentato
- **Descrizione:** Un Local Server si registra al Central System all'avvio, fornendo `buildingId` e `baseUrl`.
- **API:** `POST /internal/register` (Central System, richiede API Key)
- **Fonte:** `[SyncController.java]`, `[LocalServerRepositoryAdapter.java]`, `[init.sql central — tabella local_servers]`

---

### 1.7 Modulo: Sicurezza e PKI

#### RF-SK-01 — Autenticazione JWT con RSA Asimmetrico
- **Priorità:** M
- **Stato:** ✅ Implementato e documentato
- **Descrizione:** Tutti i JWT sono firmati con RS256 usando coppie RSA 2048-bit distinte per Central System e per ciascun Local Server.
- **Fonte:** `[JwtTokenProvider.java]`, `[JwtAuthenticationFilter.java]`, `[JwtConfig.java]`

#### RF-SK-02 — Autenticazione Server-to-Server via API Key
- **Priorità:** M
- **Stato:** ✅ Implementato e documentato
- **Descrizione:** Le comunicazioni interne (sync, registrazione, replica utenti) usano un header `X-Internal-Api-Key` con valore segreto condiviso.
- **Fonte:** `[InternalApiKeyFilter.java]`, `docker-compose.yml` (variabile `INTERNAL_API_KEY`)

#### RF-SK-03 — TLS 1.3 su REST e MQTT
- **Priorità:** M
- **Stato:** ✅ Implementato e documentato
- **Descrizione:** Tutte le comunicazioni REST (HTTPS su porta 8080/8081) e MQTT (broker su porta 8883 con SSL) usano TLS 1.3.
- **Fonte:** `docker-compose.yml`, `[infrastructure/tls/]`, `[infrastructure/mosquitto/mosquitto.conf]`

#### RF-SK-04 — PKI Dinamica per Game Client
- **Priorità:** M
- **Stato:** ✅ Implementato e documentato
- **Descrizione:** Ogni Game Client, al primo avvio, genera una coppia RSA 2048-bit e una CSR (PKCS#10), la invia al Local Server tramite `POST /api/devices/register`, e riceve un certificato X.509 firmato dalla CA locale.
- **Fonte:** `[CertificateEnrollmentService.java]` (client), BouncyCastle 1.78.1
- **Nota:** ⚠️ Durante l'enrollment iniziale, la verifica TLS del server viene bypassata (trust-all). È un rischio noto e documentato.

#### RF-SK-05 — Hash Password con BCrypt
- **Priorità:** M
- **Stato:** ✅ Implementato e documentato
- **Descrizione:** Le password degli utenti non vengono mai salvate in chiaro; viene usato BCrypt tramite `PasswordEncoder` di Spring Security.
- **Fonte:** `[PasswordEncoderConfig.java]`, `[UserService.java]`

---

### 1.8 Modulo: Resilienza e Recovery

#### RF-RE-01 — Session Recovery all'Avvio
- **Priorità:** M
- **Stato:** ✅ Implementato e documentato
- **Descrizione:** All'avvio del Local Server, le sessioni in stato `IN_PROGRESS` o `PAUSED` vengono recuperate. Il server invia un ping MQTT di recovery a ogni dispositivo e attende 30 secondi; chi non risponde viene abortito.
- **Fonte:** `[SessionRecoveryService.java]` — implementa `SmartLifecycle`, `@DependsOn("mqttClient")`

#### RF-RE-02 — Operatività Offline del Local Server
- **Priorità:** M
- **Stato:** ✅ Implementato e documentato
- **Descrizione:** Il Local Server mantiene piena operatività (prenotazioni, sessioni, MQTT, login) quando il Central System non è raggiungibile.
- **Fonte:** `[SyncSchedulerService.java]` — check `isReachable()` prima del sync

#### RF-RE-03 — Retry Automatico Sync Fallito
- **Priorità:** S
- **Stato:** ✅ Implementato e documentato
- **Descrizione:** In caso di sync fallito, il contatore `retry_count` viene incrementato e il tentativo sarà ripetuto al ciclo successivo.
- **Nota:** [DA CHIARIRE] Non esiste una soglia massima di retry documentata.

---

## 2. Requisiti Non Funzionali (RNF)

### RNF-01 — Disponibilità Locale (Offline-First)

| Attributo       | Valore                                                                                                                                    |
|-----------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| **Metrica**     | Il Local Server deve rimanere operativo al 100% anche con il Central System irraggiungibile                                               |
| **Misurazione** | Test di disconnessione: simulare assenza del Central System e verificare prenotazioni/sessioni funzionanti                                |
| **Stato**       | ✅ Garantito da DB locale MySQL + Outbox asincrono                                                                                        |

### RNF-02 — Latenza di Sincronizzazione

| Attributo   | Valore                                                                                                               |
|-------------|----------------------------------------------------------------------------------------------------------------------|
| **Metrica** | Entro 5 minuti dalla riconnessione, tutti gli eventi `PENDING` vengono inviati al Central System                    |
| **Soglia**  | Max 300 000 ms (fixedRate del `SyncSchedulerService`)                                                               |
| **Stato**   | ✅ Implementato — POF-7 risolto (lettura limitata `findPendingLimit(batchSize)` + isolamento poison per-event)   |

### RNF-03 — Latenza di Risposta API

| Attributo      | Valore                                                                                       |
|----------------|----------------------------------------------------------------------------------------------|
| **Metrica**    | Tutte le API REST devono rispondere entro 2 secondi in condizioni normali (singolo utente)   |
| **Stato**      | [DA CHIARIRE] Nessun test di performance automatico presente nel progetto                    |

### RNF-04 — Scalabilità Orizzontale degli Spoke

| Attributo   | Valore                                                                                                                                            |
|-------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| **Metrica** | L'aggiunta di un nuovo Local Server richiede solo la configurazione di `BUILDING_ID`, `CENTRAL_SYSTEM_URL`, e un record in `local_servers`        |
| **Stato**   | ✅ Architetturalmente garantito dall'Hub-and-Spoke — nel prototipo è configurato un solo edificio                                                  |

### RNF-05 — Sicurezza del Trasporto

| Attributo        | Valore                                                       |
|------------------|--------------------------------------------------------------|
| **Metrica**      | 100% delle comunicazioni (REST e MQTT) protette da TLS 1.3  |
| **Algoritmo JWT**| RS256 (RSA 2048-bit)                                         |
| **Hash password**| BCrypt (Spring Security default: 10 round)                   |
| **Stato**        | ✅ Implementato                                               |

### RNF-06 — Rilevamento Dispositivi Irraggiungibili

| Attributo      | Valore                                                                                                                               |
|----------------|--------------------------------------------------------------------------------------------------------------------------------------|
| **Metrica**    | Un dispositivo silente per 3 cicli consecutivi (15 minuti) viene dichiarato irraggiungibile e la sessione viene abortita            |
| **Precisione** | Errore massimo di un ciclo (5 minuti) rispetto alla soglia di 15 minuti                                                             |
| **Stato**      | ✅ Implementato — `[HealthCheckService.java]`                                                                                         |

### RNF-07 — Scadenza Prenotazioni

| Attributo   | Valore                                                                                     |
|-------------|--------------------------------------------------------------------------------------------|
| **Metrica** | Una prenotazione scaduta viene marcata `EXPIRED` entro 60 secondi dalla scadenza           |
| **Stato**   | ✅ Implementato — `[ReservationExpirationService.java]`, `fixedRate = 60000`               |

### RNF-08 — Manutenibilità (Clean Architecture)

| Attributo    | Valore                                                                                                                                                         |
|--------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Metrica**  | Nessuna dipendenza diretta tra domain layer e framework Spring o JPA                                                                                           |
| **Struttura**| Ogni modulo segue il layout: `domain/model`, `domain/ports/in`, `domain/ports/out`, `application/service`, `infrastructure/adapters`                          |
| **Stato**    | ✅ Rispettato in `central-system` e `local-server`                                                                                                              |

### RNF-09 — Testabilità

| Attributo      | Valore                                                                                                                                         |
|----------------|------------------------------------------------------------------------------------------------------------------------------------------------|
| **Metrica**    | Ogni `UseCase` e `Service` di dominio ha almeno un test unitario con mock dei repository                                                       |
| **Copertura**  | [DA CHIARIRE] Nessun report di copertura automatico configurato (JaCoCo non presente nel pom.xml root)                                         |
| **Stato**      | 🔶 Test unitari presenti (es. `AuthServiceTest`, `SyncReceiverServiceTest`) ma copertura non misurata formalmente                              |

### RNF-10 — Eseguibilità con Docker

| Attributo   | Valore                                                                                                                                                                           |
|-------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Metrica** | Il comando `docker-compose up --build` avvia l'intero sistema (2 DB, 1 broker MQTT, 1 Central, 1 Local Server, 2 Game Client) senza configurazione manuale aggiuntiva           |
| **Stato**   | ✅ Verificato — `docker-compose.yml` e `Dockerfile` di ogni modulo presenti e funzionanti                                                                                        |

---

## 3. Requisiti di Integrazione

### RI-01 — Protocollo MQTT (Local Server ↔ Game Client)

| Attributo            | Valore                                                                  |
|----------------------|-------------------------------------------------------------------------|
| **Broker**           | Eclipse Mosquitto 2.0                                                   |
| **Porta sicura**     | 8883 (SSL/TLS)                                                          |
| **Porta non sicura** | 1883 (solo per sviluppo locale)                                         |
| **Client library**   | Eclipse Paho 1.2.5 (`org.eclipse.paho.client.mqttv3`)                  |
| **QoS**              | QoS 1 per eventi di sessione e stato; QoS 0 per heartbeat              |
| **Retained**         | Sì, per topic `state` (ultimo stato noto sempre disponibile)            |
| **Autenticazione**   | mTLS (CN certificato client = username MQTT) in produzione; password plain in sviluppo |
| **Fonte**            | `[MqttClientConfig.java]`, `[MqttConnectionManager.java]`, `docker-compose.yml` |

**Schema topic completo:**

```
building/{buildingId}/game/{gameId}/state          QoS 1, Retained
building/{buildingId}/game/{gameId}/session/start  QoS 1
building/{buildingId}/game/{gameId}/session/end    QoS 1
building/{buildingId}/game/{gameId}/session/pause  QoS 1
building/{buildingId}/game/{gameId}/session/resume QoS 1
building/{buildingId}/game/{gameId}/heartbeat      QoS 0
building/{buildingId}/game/{gameId}/heartbeat/ack  QoS 0
building/{buildingId}/alerts                       QoS 1
```

**Fonte:** `[MqttTopics.java]` (shared-mqtt)

### RI-02 — API REST Central System

| Endpoint                 | Metodo | Auth       | Descrizione                        |
|--------------------------|--------|------------|------------------------------------|
| `/api/users`             | POST   | Pubblico   | Registrazione utente               |
| `/api/auth/login`        | POST   | Pubblico   | Login (Central)                    |
| `/api/users/{id}`        | PUT    | ROLE_ADMIN | Aggiornamento utente               |
| `/api/statistics`        | GET    | ROLE_ADMIN | Statistiche globali                |
| `/internal/sync/receive` | POST   | API Key    | Ricezione sync da Local Server     |
| `/internal/register`     | POST   | API Key    | Registrazione Local Server         |

**Fonte:** `[UserController.java]`, `[AuthController.java]`, `[StatisticsController.java]`, `[SyncController.java]`

### RI-03 — API REST Local Server

| Endpoint                    | Metodo | Auth      | Descrizione                      |
|-----------------------------|--------|-----------|----------------------------------|
| `/api/auth/login`           | POST   | Pubblico  | Login locale                     |
| `/api/reservations`         | POST   | ROLE_USER | Crea prenotazione                |
| `/api/reservations/{id}`    | DELETE | ROLE_USER | Cancella prenotazione            |
| `/api/reservations`         | GET    | ROLE_USER | Lista prenotazioni               |
| `/api/games`                | GET    | ROLE_USER | Lista giochi                     |
| `/api/games/available`      | GET    | ROLE_USER | Giochi disponibili               |
| `/api/sessions/start`       | POST   | ROLE_USER | Avvia sessione                   |
| `/api/sessions/{id}/end`    | POST   | ROLE_USER | Termina sessione                 |
| `/api/sessions/{id}/pause`  | POST   | ROLE_USER | Pausa sessione                   |
| `/api/sessions/{id}/resume` | POST   | ROLE_USER | Riprendi sessione                |
| `/api/statistics`           | GET    | ROLE_USER | Statistiche locali               |
| `/internal/users/sync`      | PUT    | API Key   | Sync utenti dal Central          |
| `/api/devices/register`     | POST   | Pubblico  | Registrazione device con CSR     |

### RI-04 — Serializzazione JSON con Polimorfismo

| Attributo        | Valore                                                                                        |
|------------------|-----------------------------------------------------------------------------------------------|
| **Libreria**     | Jackson 2.17.2 (override esplicito rispetto a Spring Boot default)                            |
| **Polimorfismo** | `@JsonTypeInfo` su `GameResult` per gestire risultati di gioco diversi per tipo              |
| **Date/Time**    | `jackson-datatype-jsr310` per `Instant`, `LocalDate`, `ZonedDateTime`                        |
| **Fonte**        | `pom.xml` root, `[GameResult.java]` (shared-domain)                                          |

---

## 4. Requisiti di Dati

### 4.1 Schema Dati — Central System

| Tabella                 | Descrizione                                                    | Chiave primaria                                     |
|-------------------------|----------------------------------------------------------------|-----------------------------------------------------|
| `users`                 | Registro globale utenti (Source of Truth)                      | `id` UUID                                           |
| `game_catalog`          | Catalogo globale dei dispositivi di gioco per edificio         | `id` UUID                                           |
| `aggregated_statistics` | Statistiche aggregate per (edificio, tipo gioco, periodo)      | `id` UUID, UK su `(building_id, game_type, period_start)` |
| `processed_events`      | Idempotency store per eventi di sync ricevuti                  | `event_id` UUID                                     |
| `local_servers`         | Registro dei Local Server registrati                           | `id` UUID, UK su `building_id`                      |
| `outbox_events`         | Coda eventi da propagare ai Local Server                       | `id` UUID                                           |

**Fonte:** `[infrastructure/mysql-central/init.sql]`

### 4.2 Schema Dati — Local Server

| Tabella                  | Descrizione                                                  | Chiave primaria              |
|--------------------------|--------------------------------------------------------------|------------------------------|
| `users`                  | Replica locale (lookup locale)                               | `id` UUID                    |
| `game_catalog`           | Catalogo locale dei dispositivi                              | `id` UUID                    |
| `reservations`           | Prenotazioni dei tavoli                                      | `id` UUID                    |
| `game_sessions`          | Sessioni di gioco con risultati JSON                         | `id` UUID                    |
| `session_participants`   | Partecipanti per sessione (relazione N:M)                    | `(session_id, user_id)`      |
| `outbox_events`          | Coda eventi da sincronizzare col Central System              | `id` UUID                    |
| `replicated_users`       | Utenti replicati dal Central per login offline               | `user_id` UUID               |
| `local_statistics_cache` | Cache statistiche locali pre-calcolate                       | `id` UUID, UK su `(game_type, period)` |

**Fonte:** `[infrastructure/mysql-local/init.sql]`

### 4.3 Volumi Attesi

| Entità                       | Volume stimato (prototipo)         | Volume stimato (produzione) |
|------------------------------|------------------------------------|-----------------------------|
| Utenti registrati            | < 100                              | [DA CHIARIRE]               |
| Dispositivi per edificio     | 2 (prototipo: foosball, chess)     | 10–50                       |
| Prenotazioni/giorno/edificio | < 50                               | [DA CHIARIRE]               |
| Sessioni/giorno/edificio     | < 100                              | [DA CHIARIRE]               |
| Outbox events (picco)        | < 1 000                            | [DA CHIARIRE] — ⚠️ POF-3 residuo Central |

### 4.4 Retention e Privacy

| Dato                            | Retention attuale              | Nota                                                               |
|---------------------------------|--------------------------------|---------------------------------------------------------------------|
| `outbox_events` (SENT) — Local  | Purge dopo 7gg (`OutboxPurgeService`)   | ✅ **POF-3 risolto (Local):** cleanup via `app.outbox-purge-retention-days` (default 7) |
| `outbox_events` (SENT) — Central | Nessuna politica di cleanup           | ⚠️ **POF-3 (residuo Central):** crescita illimitata; nessun TTL/purge centrale configurato |
| `processed_events`              | Nessuna politica di cleanup    | [DA CHIARIRE] può generare crescita indefinita                     |
| `game_sessions` / `reservations`| Permanenti                     | Nessun archivio o purge pianificato                                |
| Password utente                 | Hash BCrypt; mai in chiaro     | ✅ Conforme                                                        |
| Email utente                    | Opzionale, non cifrata a riposo| ⚠️ Per conformità GDPR completa, la cifratura a riposo è raccomandata |
| Diritto all'oblio (GDPR)        | Non implementato               | 📋 Da implementare per conformità completa                        |

---

## 5. Requisiti di Infrastruttura

### 5.1 Componenti Docker

| Servizio         | Immagine                          | Porta host | Rete Docker                        | Ruolo                            |
|------------------|-----------------------------------|------------|------------------------------------|----------------------------------|
| `central-db`     | `mysql:8.0`                       | 3306       | `central-net`                      | DB del Central System            |
| `central-system` | Build da `./central-system`       | 8080       | `central-net`, `integration-net`   | Central System Spring Boot       |
| `local-db-1`     | `mysql:8.0`                       | 3307       | `local-net-1`                      | DB del Local Server (edificio 1) |
| `mqtt-broker-1`  | `eclipse-mosquitto:2.0`           | 8883, 1883 | `local-net-1`                      | Broker MQTT locale               |
| `local-server-1` | Build da `./local-server`         | 8081       | `local-net-1`, `integration-net`   | Local Server (edificio 1)        |
| `game-client-1`  | Build da `./game-client-emulator` | —          | `local-net-1`                      | Emulatore FOOSBALL               |
| `game-client-2`  | Build da `./game-client-emulator` | —          | `local-net-1`                      | Emulatore CHESS                  |

**Fonte:** `[docker-compose.yml]`

### 5.2 Reti Docker

```mermaid
graph LR
    CS[central-system] --- CN[(central-net)]
    CDB[(central-db)] --- CN
    CS --- IN[(integration-net)]
    LS[local-server-1] --- IN
    LS --- LN[(local-net-1)]
    LDB[(local-db-1)] --- LN
    MB[mqtt-broker-1] --- LN
    GC1[game-client-1] --- LN
    GC2[game-client-2] --- LN
```

- **`central-net`**: isolata, contiene solo Central System e il suo DB.
- **`local-net-1`**: isolata per edificio 1, contiene Local Server, DB locale, MQTT broker e Game Client.
- **`integration-net`**: rete condivisa tra Central System e Local Server per le comunicazioni REST interne.

### 5.3 Variabili d'Ambiente Obbligatorie

| Variabile                   | Componente     | Descrizione                                           |
|-----------------------------|----------------|-------------------------------------------------------|
| `INTERNAL_API_KEY`          | Central, Local | Segreto condiviso per autenticazione server-to-server |
| `CENTRAL_DB_PASSWORD`       | Central DB     | Password root MySQL Central (default: `root`)         |
| `LOCAL_DB_PASSWORD`         | Local DB       | Password root MySQL Local (default: `root`)           |
| `GAME_CLIENT_MQTT_PASSWORD` | Game Client    | Password per autenticazione MQTT dei client           |
| `BUILDING_ID`               | Local Server   | Identificatore dell'edificio (es. `building-1`)       |
| `SYNC_INTERVAL_MS`          | Local Server   | Intervallo sync in ms (default: `300000`)             |
| `HEALTHCHECK_INTERVAL_MS`   | Local Server   | Intervallo health check in ms (default: `300000`)     |

### 5.4 Requisiti Hardware Minimi (per esecuzione prototipo)

| Risorsa  | Minimo raccomandato                                                                    |
|----------|----------------------------------------------------------------------------------------|
| RAM      | 8 GB (Docker Desktop richiede 4 GB; l'insieme dei servizi ne usa ~3–4 GB aggiuntivi)  |
| CPU      | 4 core                                                                                 |
| Storage  | 10 GB liberi (immagini Docker + volumi MySQL)                                          |
| OS       | Windows 10/11 con Docker Desktop, o Linux/macOS con Docker Engine                     |

### 5.5 Moduli Maven (Monorepo)

| Modulo                   | Tipo        | Descrizione                                            |
|--------------------------|-------------|--------------------------------------------------------|
| `shared/shared-domain`   | Library     | Value objects, enums, domain interfaces condivisi      |
| `shared/shared-dto`      | Library     | DTO per comunicazione REST e sync tra moduli           |
| `shared/shared-mqtt`     | Library     | `MqttTopics`, payload MQTT condivisi                   |
| `central-system`         | Application | Spring Boot, Central System                            |
| `local-server`           | Application | Spring Boot, Local Server (Edge Node)                  |
| `game-client-emulator`   | Application | JavaFX + MQTT, Game Client Emulator                    |

**Fonte:** `[pom.xml]` root — `<modules>` section

---

## 6. Matrice di Tracciabilità

### 6.1 Requisiti Funzionali ↔ Componenti

| Requisito | Modulo applicativo           | File chiave                                                           | Stato           |
|-----------|------------------------------|-----------------------------------------------------------------------|-----------------|
| RF-AU-01  | Central System               | `UserController.java`, `UserService.java`, `init.sql` (central)      | ✅              |
| RF-AU-02  | Central System               | `AuthController.java`, `AuthService.java`, `JwtTokenProvider.java`   | ✅              |
| RF-AU-03  | Local Server                 | `LocalAuthService.java`, `replicated_users` (local)                  | ✅              |
| RF-AU-04  | Central System               | `UserController.java`, `UserService.java`                            | ✅              |
| RF-AU-05  | Central System, Local Server | `SecurityConfig.java`, `JwtAuthenticationFilter.java`                | ✅              |
| RF-PR-01  | Local Server                 | `ReservationService.java`, `reservations` (local)                    | ✅             |
| RF-PR-02  | Local Server                 | `ReservationService.java`                                            | ✅              |
| RF-PR-03  | Local Server                 | `ReservationService.java`, `ReservationRepository`                   | ✅              |
| RF-PR-04  | Local Server                 | `ReservationExpirationService.java`                                  | ✅              |
| RF-SE-01  | Local Server                 | `GameSessionService.java`                                            | ✅              |
| RF-SE-02  | Local Server                 | `GameSessionService.java`, `GameResult.java`                         | ✅              |
| RF-SE-03  | Local Server                 | `GameSessionService.java`                                            | ✅              |
| RF-SE-04  | Local Server                 | `HealthCheckService.java`                                            | ✅              |
| RF-GS-01  | Local Server                 | `GameStateService.java`                                              | ✅              |
| RF-GS-02  | Local Server                 | `GameStateService.java`                                              | ✅              |
| RF-GS-03  | Local Server, Game Client    | `GameStatePublisher.java`, `PublishGameStatePort`, `MqttTopics.java` | ✅              |
| RF-GS-04  | Local Server, Game Client    | `HealthCheckService.java`, `HeartbeatService.java`                   | ✅              |
| RF-ST-01  | Local Server                 | `StatisticsService.java`, `LocalStatistics.java`                     | ✅              |
| RF-ST-02  | Central System               | `StatisticsController.java`, `StatisticsAggregationService.java`     | ✅              |
| RF-SY-01  | Local Server                 | `SyncSchedulerService.java`                                          | ✅             |
| RF-SY-02  | Central System               | `SyncController.java`, `SyncReceiverService.java`                    | ✅              |
| RF-SY-03  | Central System               | `UserReplicationSchedulerService.java`                               | ✅              |
| RF-SY-04  | Central System               | `SyncController.java`, `LocalServerRepositoryAdapter.java`           | ✅              |
| RF-SK-01  | Central System, Local Server | `JwtTokenProvider.java`, `JwtAuthenticationFilter.java`              | ✅              |
| RF-SK-02  | Central System, Local Server | `InternalApiKeyFilter.java`                                          | ✅              |
| RF-SK-03  | Tutti                        | `docker-compose.yml`, `infrastructure/tls/`                          | ✅              |
| RF-SK-04  | Game Client, Local Server    | `CertificateEnrollmentService.java`                                  | ✅              |
| RF-SK-05  | Central System               | `PasswordEncoderConfig.java`, `UserService.java`                     | ✅              |
| RF-RE-01  | Local Server                 | `SessionRecoveryService.java`                                        | ✅              |
| RF-RE-02  | Local Server                 | `SyncSchedulerService.java`                                          | ✅              |
| RF-RE-03  | Local Server                 | `SyncSchedulerService.java`                                          | ✅              |

### 6.2 Known Issues ↔ Requisiti Impattati

| Issue | Descrizione                                                                                            | RF impattati | Severità | Stato risoluzione |
|-------|--------------------------------------------------------------------------------------------------------|--------------|----------|-------------------|
| POF-3 | Outbox unbounded growth: cleanup/TTL su `outbox_events` (SENT)                                         | RF-SY-01     | Media    | 🟡 Risolto lato Local (`OutboxPurgeService` + `OutboxDlqPromotionService`); **aperto lato Central** (nessun purge/DLQ centrale) |
| POF-5 | Race condition MQTT/REST: optimistic locking su `game_catalog`/`reservations`                          | RF-PR-01     | Alta     | 🟢 Risolto (`@Version` su `GameJpaEntity`/`ReservationJpaEntity` + `ConcurrentStateException` → 409 REST / ack-and-drop MQTT); residuo: `GameSessionJpaEntity` senza `@Version` |
| POF-7 | Sync starvation: lettura outbox senza paginazione; backlog grande può saturare memoria                 | RF-SY-01     | Alta     | 🟢 Risolto (`findPendingLimit(batchSize)` + poison isolation per-event + `markAsSentBatch` atomico + promozione DLQ via `OutboxDlqPromotionService`) |

### 6.3 Giochi Supportati ↔ Stato

| Gioco        | Enum `GameType` | Game Client Emulator          | Logica di risultato | Stato          |
|--------------|-----------------|-------------------------------|---------------------|----------------|
| FOOSBALL     | ✅              | ✅ `FoosballGame.java`        | ✅                  | ✅ Operativo   |
| CHESS        | ✅              | ✅ `ChessGame.java`           | ✅                  | ✅ Operativo   |
| DARTS        | ✅              | ✅ `DartsGame.java`           | ✅                  | ✅ Operativo   |
| MONOPOLY     | ✅              | ✅ `MonopolyGame.java`        | ✅                  | ✅ Operativo   |
| RISK         | ✅              | ✅ `RiskGame.java`            | ✅                  | ✅ Operativo   |
| SLOT_MACHINE | ✅              | ✅ `SlotMachineGame.java`     | ✅                  | 📋 Roadmap     |
| ROULETTE     | ✅              | ✅ `RouletteGame.java`        | ✅                  | 📋 Roadmap     |

> **Nota:** SLOT_MACHINE e ROULETTE hanno classi emulatore e record di risultato presenti ma non sono integrati nelle configurazioni di deploy né nel catalogo giochi del prototipo.

---

*Fine documento REQUIREMENTS.md*
*Vedere [DESIGN.md](DESIGN.md) per le scelte architetturali e la struttura del sistema.*
*Vedere [IMPLEMENTATION.md](IMPLEMENTATION.md) per la guida al setup e al deployment.*
