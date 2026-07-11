# Boardgame Platform

Piattaforma distribuita per la gestione di giochi da tavolo e da bar (calciobalilla, scacchi, freccette, monopoli, ecc.). 
Il sistema si basa sul paradigma dell'**Edge Computing** combinato con i principi dei **Microservizi** e della **Clean Architecture** (Architettura Esagonale), per garantire massima resilienza offline, scalabilità orizzontale e manutenibilità.

---

## 1. Architettura del Sistema

L'architettura segue il **Pattern Hub-and-Spoke**, arricchito da una comunicazione **Pub/Sub via MQTT** a livello locale.
Si divide in tre componenti principali e un ecosistema di librerie condivise:

* **Central System (L'Hub)**: Server principale (Spring Boot) che funge da *Source of Truth* globale (utenti, statistiche aggregate). Usa un DB MySQL centralizzato.
* **Local Server (Lo Spoke / Edge Node)**: Microservizio (Spring Boot) installato nell'edificio locale. Gestisce le sessioni di gioco ed è *Offline-First*. Usa un DB MySQL locale e comunica col Central via pattern *Transactional Outbox*.
* **Game Client Emulator (L'Endpoint)**: App client con interfaccia grafica in JavaFX. Parla *esclusivamente* col Local Server via **MQTT over TLS** assicurando disaccoppiamento totale.
* **Moduli Condivisi (Shared)**: Librerie monorepo Maven (`shared-domain`, `shared-dto`, `shared-mqtt`) per evitare codice duplicato tra i microservizi.

---

## 2. Requisiti di Sistema
Per lo sviluppo attivo del progetto sono richiesti:
- **IntelliJ IDEA** (Community o Ultimate Edition)
- **Docker Desktop** (avviato e funzionante)
- **Git** (per il versioning)
- *Nota: Java 21 e Maven saranno gestiti internamente dall'IDE.*

---

## 3. Flusso di Lavoro Consigliato: L'Approccio "Ibrido"

Per lo sviluppo in team abbiamo adottato un **approccio Ibrido**. Questo approccio separa la parte "infrastrutturale" (che è tediosa da installare a mano) dalla parte "applicativa" (che vogliamo compilare ed eseguire alla massima velocità).
* **Docker** si occuperà *solo* di far girare i Database (MySQL) e il Broker di messaggistica (Mosquitto). In questo modo, le porte, le password e l'ambiente dei DB saranno identici per tutti i membri del team.
* **IntelliJ IDEA** si occuperà nativamente di gestire Java, scaricare le librerie (Maven) ed eseguire le nostre applicazioni Spring Boot.

Di seguito i passi per configurare da zero la propria postazione.

### Step 1: Configurazione di IntelliJ IDEA e Java 21
Non è necessario installare Java manualmente nel sistema operativo. L'IDE farà tutto per voi garantendo omogeneità nel team.
1. Aprite IntelliJ IDEA e caricate la cartella radice del progetto `gamehandler-platform`.
2. L'IDE individuerà automaticamente i file `pom.xml` e comincerà a indicizzare il progetto Maven (attendete la fine del processo in basso a destra).
3. Cliccate su **File > Project Structure** (`Ctrl+Alt+Shift+S`).
4. Sotto `Project Settings > Project`, alla voce **SDK**, aprite il menù a tendina.
5. Selezionate **Download JDK...**
6. Scegliete la versione **21** e come Vendor selezionate **Eclipse Temurin** (o Amazon Corretto). 
7. Cliccate "Download" e poi "Apply". Ora l'intero progetto è mappato su una versione pulita e standard di Java 21.

### Step 2: Inizializzare l'Infrastruttura (Il ruolo di Docker)
Installare docker per windows [da qui](https://desktop.docker.com/win/main/amd64/Docker%20Desktop%20Installer.exe?utm_source=docker&utm_medium=webreferral&utm_campaign=docs-driven-download-win-amd64).  
I file di configurazione (`docker-compose.yml`) definiscono l'infrastruttura. Avviare questi servizi è il prerequisito prima di far partire il codice Java.
1. Aprite il terminale di IntelliJ (in basso) oppure usate la GUI del plugin Docker integrato nell'IDE aprendo il file `docker-compose.yml`, premere il doppio pulsante di play (quello con due triangoli verdi sovvrapposti).
2. Per avviare **esclusivamente** i Database e il Broker MQTT (senza avviare i server Java containerizzati), lanciate questo comando:
   ```bash
   docker-compose up -d central-db local-db-1 mqtt-broker-1
   ```
   *Spiegazione tecnica:* `up` costruisce e avvia i container. L'opzione `-d` (detached) li fa girare in background lasciando libero il terminale. Fornendo i nomi dei singoli servizi, diciamo a Docker di accendere *solo* l'infrastruttura di base e non i microservizi Java.
3. Se i container sono partiti, avrete ora un MySQL in ascolto per il Central System, un MySQL per il Local Server e un broker Mosquitto in ascolto. Potete verificarne lo stato con `docker-compose ps`.

### Step 3: Sviluppo ed Esecuzione del Codice (Spring Boot)
Ora che l'infrastruttura è attiva in background, potete scrivere il codice.

> IMPORTANTE:
> Per provare il sistema ricordarsi sempre di aver avviato i container Docker.

Quando volete testare il sistema:
1. Cercate nel progetto le classi `main` dei tre microservizi 
   - `CentralSystemApplication.java`
   - `LocalServerApplication.java`
   - `GameClientEmulatorApplication.java`
2. Cliccate sulla freccia verde **Play** (Run) a fianco della classe in IntelliJ.
3. Le applicazioni Spring Boot partiranno nativamente sul vostro PC, leggeranno i file `application.yml` che puntano a `localhost:3306` (dove Docker sta inoltrando il DB) e si connetteranno correttamente.
Questo vi permette di usare il Debugger di IntelliJ in modo fulmineo, senza riavviare Docker a ogni singola riga di codice modificata!

> Attenzione: Se volete avviare i sistemi dal docker, ricordarsi di averli compilati in jar prima (altrimenti si ottiene l'errore di targen mancante).
---

## 4. Ambiente di Produzione (Per l'Esame e la Consegna)

Mentre l'Approccio Ibrido è perfetto per lo *sviluppo*, il professore dovrà poter eseguire il progetto completo in un solo click, senza dover aprire IntelliJ o configurare Java. 

Per l'ambiente di produzione (o "Test Finale" per voi prima della consegna), si sfrutta **interamente** la potenza di Docker. I `Dockerfile` **non compilano** il sorgente dentro il container: consumano artefatti Maven **già buildati** (`COPY target/*.jar`), quindi il `mvn clean package -DskipTests` dello step 2 è prerequisito obbligatorio prima del `docker-compose up --build`. Il file `docker-compose.yml` inoltre dichiara blocchi `healthcheck:` per `central-system`/`local-server` e per i rispettivi DB, con `depends_on` a condizione `service_healthy` per i DB (e `service_started` per il broker MQTT): in questo modo `docker-compose up` attende che i database siano sani prima di avviare i server.

### Come simulare la Produzione:
1. Fermate tutte le istanze avviate in IntelliJ.
2. Compilate prima i pacchetti Maven (per generare i file `.jar` necessari al Docker) eseguendo:
   ```bash
   mvn clean package -DskipTests
   ```
3. Aprite il terminale e lanciate l'accensione **globale** (senza specificare i nomi dei servizi):
   ```bash
   docker-compose up -d --build
   ```
   *Questo comando tira su: 2 Database, 1 Broker MQTT, 1 Central System, 1 Local Server e 2 Simulatori Client. Tutto il sistema sarà vivo all'interno della rete virtuale di Docker.*
4. Per visualizzare i log live del sistema in esecuzione:
   ```bash
   docker-compose logs -f
   ```
5. Per spegnere tutto una volta terminata la simulazione o la correzione dell'esame:
   ```bash
   docker-compose down
   ```
   *(Nota: Aggiungendo il flag `-v` a fine comando, Docker eliminerà anche i volumi, azzerando completamente tutti i dati nei database).*

---

## 5. Comandi di Manutenzione (Utility)

### Gestione Password MQTT (Mosquitto)
Se è necessario aggiornare il file `password_file` del broker MQTT (attualmente configurato senza crittografia esplicita al solo scopo prototipale), è possibile rigenerare gli hash sfruttando un container effimero senza installare `mosquitto_passwd` sul proprio OS:
```bash
docker run --rm -v ${PWD}/infrastructure/mosquitto:/mosquitto/config eclipse-mosquitto:2.0 sh -c "mosquitto_passwd -c -b /mosquitto/config/password_file client-foosball-1 foosball_password"
```

---

## 6. Smoke test (Docker)

Procedura manuale per verificare il flusso end-to-end su Docker. Documentata come riferimento per la FASE 4 step 5 (avvio reale) del piano `workflow/analisi/risoluzione_comunicazioni_local_central.md` — non eseguita in CI perché richiede Docker daemon attivo.

### Prerequisiti
- Docker + Docker Compose
- Porte libere: 8080, 8081, 1883, 3306, 3307

### Avvio
```bash
cd gamehandler-platform
docker-compose up -d --build central-db local-db-1 mqtt-broker-1
mvn spring-boot:run -pl central-system
# in un altro terminale:
mvn spring-boot:run -pl local-server
```

### Verifica (10-15 min di osservazione)
1. **Auto-registrazione**: nel log del local compare `Local server registered with central system`. Nel DB central: `SELECT * FROM central_db.local_servers;` → riga `building-1` con `is_active=1`, `base_url=https://local-server-1:8081`.
2. **Replica utenti**: registra un utente sul local → entro 5 min l'utente appare in `central_db.users`. Il central scheduler replica verso il local → l'utente appare in `local_db.users`.
3. **Outbox**: `SELECT status, COUNT(*) FROM local_db.outbox_events GROUP BY status;` → PENDING → 0, SENT cresce, FAILED → 0 (promosso a DLQ da `OutboxDlqPromotionService`).
4. **Statistiche**: `SELECT * FROM central_db.aggregated_statistics;` → `total_sessions`, `total_aborted_sessions`, `total_reservations` popolati.
5. **Log**: nessun `ERROR` o `WARN` inatteso.

### Comandi curl di verifica

La dipendenza `spring-boot-starter-actuator` (runtime) è ora presente nei `pom.xml` di `central-system` e `local-server`, e in entrambi gli `application.yml` è esposto solo l'endpoint `health` (`management.endpoints.web.exposure.include: health`). Il path `/actuator/health` è inoltre in `permitAll` in entrambi i `SecurityConfig`, quindi i seguenti curl funzionano senza credenziali:

```bash
# Health check central
curl -k https://localhost:8080/actuator/health
# Health check local
curl -k https://localhost:8081/actuator/health
```

> Nota: `curl` è installato anche dentro le immagini Docker (entry `RUN apt-get install -y curl` nei `Dockerfile`), così gli `healthcheck:` del compose possono usare `curl -kfsS https://localhost:808x/actuator/health`.

---

## Multi-building smoke test

Run the multi-building composition (building-1 from `docker-compose.yml` + building-2 and building-3 from the override):
```bash
docker compose -f docker-compose.yml -f docker-compose.multi.yml up
```

Recommended environment overrides for a 15-minute smoke run (avoids health-monitor flapping and reconciliation re-push noise):
- `SERVER_STALE_THRESHOLD_MS=3600000` (1 hour — prevents `LocalServerHealthMonitorService` from deactivating buildings during the smoke)
- `RECONCILIATION_INTERVAL_MS` left at the 1-hour default OR set high to silence `UserReplicationReconciliationService` INFO logs

The composition provisions `local-db-2`/`local-db-3` (host ports 3308/3309) with `init-building-2.sql`/`init-building-3.sql`, `mqtt-broker-2`/`mqtt-broker-3` (host ports 8884/8885 — separate brokers for namespace isolation because the base `mosquitto.conf` has no ACL), and `local-server-2`/`local-server-3` with `BUILDING_ID=building-2`/`building-3`.

### Smoke scenarios (also covered by `MultiBuildingEndToEndIT`)
1. building-2 + building-3 self-register → both rows in `local_servers`.
2. `USER_REGISTERED` at building-2 → replicated to building-1 AND building-3 (two `replication_progress` rows; event SENT).
3. `GAME_SESSION_COMPLETED` for building-2/CHESS and building-3/FOOSBALL → two distinct `aggregated_statistics` rows, no cross-building pollution.
4. re-send same `USER_REGISTERED` from building-2 → no second push (`processed_events` dedup).

### Operator notes
- **TLS SAN**: `infrastructure/tls/generate-certs.ps1` attualmente include nella SAN solo `local-server-1`/`localhost`/`127.0.0.1` (vedi `local-server-https.ext`). Lo smoke multi-building richiede pertanto di estendere lo script per aggiungere `local-server-2`/`local-server-3` alla SAN; SAN mancanti causano failure di handshake TLS tra central e i nuovi local server.
- **MQTT namespaces**: each building uses its own broker (`mqtt-broker-2`/`mqtt-broker-3`) so topic paths like `building/bld-1/...` and `building/bld-2/...` are physically separated (the brokers don't share state).
- **Heartbeats**: send one `POST /internal/servers/register` from each building within the `SERVER_STALE_THRESHOLD_MS` window to keep `is_active=true` (the build is auto-registered on startup; the heartbeat guidance is for long smoke runs that exceed the threshold).
