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
Installare Docker per Windows [da qui](https://desktop.docker.com/win/main/amd64/Docker%20Desktop%20Installer.exe?utm_source=docker&utm_medium=webreferral&utm_campaign=docs-driven-download-win-amd64).
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
Questo vi permette di usare il Debugger di IntelliJ in modo fulmineo, senza riavviare Docker a ogni singola riga di codice modificato!

> Attenzione: Se volete avviare i sistemi dal docker, ricordarsi di averli compilati in jar prima (altrimenti si ottiene l'errore di target mancante).

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

## 6. Accesso ai pannelli di gestione

Questa sezione documenta come accedere ai pannelli amministrativi del **Central System** e del **Local Server**, gli endpoint di autenticazione, i ruoli e la procedura di bootstrap del primo amministratore di piattaforma.

> **Nessun utente seed.** Gli `init.sql` di `infrastructure/mysql-central` e `infrastructure/mysql-local` **non contengono** `INSERT INTO users` (né in `users` né in `replicated_users`): sono seminati solo `game_catalog` e `game_definitions`. Non esistono credenziali admin predefinite: il primo `PLATFORM_ADMIN` va registrato e poi promosso manualmente via SQL (procedura in §6.1).

### 6.1 Central System (`https://localhost:8180`)

- **Endpoint API base**: `https://localhost:8180` (HTTPS, keystore `central-system-https.p12`).
- **Porta**: `server.port` → `${PORT:8180}` in `central-system/src/main/resources/application.yml` (override via env `PORT`).
- **Pannello amministrativo**: non esiste UI web dedicata. La gestione avviene via **API REST** (es. il Game Client Emulator in JavaFX con login come `PLATFORM_ADMIN`, oppure `curl`).
- **Autenticazione** — `POST /api/auth/login` (`AuthController.java:31`) → JWT Bearer. Risposta `LoginResponseDto { token, userId, expiresAt }`; scadenza default 24 h (`jwt.expiration-ms`). Il token va inviato come `Authorization: Bearer <token>` sulle richieste protette:
  ```bash
  curl -k -X POST https://localhost:8180/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"password-scelta"}'
  ```
- **Endpoint pubblici** (`SecurityConfig.java:40-43`): `POST /api/auth/**`, `POST /api/users` (registrazione), `/internal/**` (filtro `InternalApiKeyFilter`), `/actuator/health`. Tutto il resto richiede JWT.
- **Ruoli** (enum `Role` in `shared/shared-domain/.../security/Role.java`): `PLATFORM_ADMIN` (utenti/building/statistiche globali), `LOCAL_ADMIN` (giochi/device/sessioni di un building, con binding in `local_admin_buildings`), `GAME_ADMIN` (game definitions e regole di registrazione), `PLAYER` (partite, statistiche e tornei personali). Un utente può avere più ruoli (CSV in `users.roles`).
- **Registrazione utente (public)** — `POST /api/users` (`UserController.java:32`) con `CreateUserRequestDto { username, password, email }` → crea **sempre** un `PLAYER` (ruolo hardcoded in `UserService.register`, `UserService.java:70`; password hashata con BCrypt, `UserService.java:68`). Vincoli: `username` 3–50 char, `password` ≥ 8 char, `email` valida:
  ```bash
  curl -k -X POST https://localhost:8180/api/users \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"password-scelta","email":"admin@example.com"}'
  ```

#### Bootstrap del primo `PLATFORM_ADMIN`
Non esiste un endpoint pubblico di promozione ruolo: `POST /api/admin/users/{userId}/roles` (sul **Local Server**) richiede già `PLATFORM_ADMIN` (`@PreAuthorize` in `PlatformAdminUserController.java:29` — problema chicken-and-egg). Procedura consigliata:
1. Registra un utente sul Central come sopra (ottiene ruolo `PLAYER`).
2. Promuovi a `PLATFORM_ADMIN` direttamente sul DB centrale:
   ```sql
   UPDATE central_db.users SET roles = 'PLATFORM_ADMIN' WHERE username = 'admin';
   ```
3. Effettua il login su `https://localhost:8180/api/auth/login` per ottenere il JWT e usalo sulle API admin (`/api/admin/**`, `/api/admin/local/buildings`, ecc.).
4. (Facoltativo) Assegna un building a un `LOCAL_ADMIN` tramite l'API central `POST /api/admin/local/buildings` (`LocalAdminController.java:46`) o direttamente sulla tabella `local_admin_buildings`.

### 6.2 Local Server (`https://localhost:8181`)

- **Endpoint API base**: `https://localhost:8181` (HTTPS, keystore `local-server-https.p12`).
- **Porta**: `server.port` → `${PORT:8181}` in `local-server/src/main/resources/application.yml` (override via env `PORT`).
- **Pannello amministrativo**: via **Game Client Emulator** (JavaFX) con login come `LOCAL_ADMIN` / `PLATFORM_ADMIN`, oppure via API REST.
- **Autenticazione** — `POST /api/auth/login` (`AuthController.java:47`) → JWT Bearer (stesso formato del Central):
  ```bash
  curl -k -X POST https://localhost:8181/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"password-scelta"}'
  ```
- **Registrazione player (public)** — `POST /api/auth/signup` (`AuthController.java:53`) con `SignupRequestDto { username, password, email }` → registra un utente locale (ruolo `PLAYER` di default). Risposta `SignupResponseDto { userId, username, email }`:
  ```bash
  curl -k -X POST https://localhost:8181/api/auth/signup \
    -H "Content-Type: application/json" \
    -d '{"username":"player1","password":"password-scelta","email":"player1@example.com"}'
  ```
- **Identità corrente** — `GET /api/auth/me` (`AuthController.java:76`, richiede JWT) → `UserInfoDto` con `userId`, `roles` e, per `LOCAL_ADMIN`, i `buildings` di competenza (da `local_admin_buildings_local`).
- **Endpoint pubblici** (`SecurityConfig.java:36-39`): `POST /api/auth/**`, `/internal/**` (filtro `InternalApiKeyFilter`), `/actuator/health`. Il resto richiede JWT; gli endpoint `/api/admin/**` richiedono inoltre `@PreAuthorize` per ruolo.
- **Gestione ruoli (PLATFORM_ADMIN)** — `POST /api/admin/users/{userId}/roles` (`PlatformAdminUserController.java:44`) con body `["PLATFORM_ADMIN","LOCAL_ADMIN"]` → emette una admin-request async (outbox); il Central replica il cambio ruolo al Local (`replicated_users`).
- **Replica utenti**: il Local Server mantiene la tabella `replicated_users` (push dal Central via outbox `USER_REGISTERED`/`USER_UPDATED`). Un utente registrato sul Central compare qui dopo un ciclo di replica (default 5 min, `app.sync-interval-ms`).

---

## 7. Configurazione di rete e porte

Tutte le porte dei servizi sono **config-driven**: niente porte hardcoded nei sorgenti Java. I default sono definiti nei `application.yml` (con override via env var `${VAR:default}`) o in `docker-compose.yml`/`mosquitto.conf` per l'infrastruttura.

### 7.1 Tabella riepilogativa porte

| Servizio | Porta host | Protocollo | Property / origine | Env var override |
|---|---|---|---|---|
| Central System HTTPS | 8180 | HTTPS | `server.port` (`central-system/src/main/resources/application.yml`) | `PORT` |
| Local Server HTTPS | 8181 | HTTPS | `server.port` (`local-server/src/main/resources/application.yml`) | `PORT` |
| MySQL central | 3306 | TCP | `docker-compose.yml` (`central-db`) + `spring.datasource.url` | `SPRING_DATASOURCE_URL` |
| MySQL local (building-1) | 3307 | TCP | `docker-compose.yml` (`local-db-1`) + `spring.datasource.url` | `SPRING_DATASOURCE_URL` |
| MySQL local (building-2) | 3308 | TCP | `docker-compose.multi.yml` (`local-db-2`) + `init-building-2.sql` | — |
| MySQL local (building-3) | 3309 | TCP | `docker-compose.multi.yml` (`local-db-3`) + `init-building-3.sql` | — |
| MQTT broker building-1 (TCP) | 1883 | TCP | `mosquitto.conf` `listener 1883` + `mqtt.broker-url` | `MQTT_BROKER_URL` |
| MQTT broker building-1 (TLS) | 8883 | TCP/SSL | `mosquitto.conf` `listener 8883` + `mqtt.broker-url` (`ssl://...`) | `MQTT_BROKER_URL` |
| MQTT broker building-2 | 8884 | TCP | `docker-compose.multi.yml` (`mqtt-broker-2`) | `MQTT_BROKER_URL` |
| MQTT broker building-3 | 8885 | TCP | `docker-compose.multi.yml` (`mqtt-broker-3`) | `MQTT_BROKER_URL` |
| MQTT broker WebSocket | — | — | non esposto (nessun `listener` WebSocket in `mosquitto.conf`) | — |
| Internal API key | — | header `X-Internal-Api-Key` | `internal.api-key` (`application.yml` di central + local) | `INTERNAL_API_KEY` |
| CORS (client web) | 3000 | HTTP (default) | `cors.allowed-origins` (central) | `CORS_ALLOWED_ORIGINS` |
| Actuator health | = server.port | HTTPS | `management.endpoints.web.exposure.include: health` | — |

### 7.2 URL di avvio dei servizi

| Servizio | URL di avvio | Come si avvia |
|---|---|---|
| Central System | `https://localhost:8180` | `mvn spring-boot:run -pl central-system` oppure IntelliJ su `CentralSystemApplication` |
| Local Server (building-1) | `https://localhost:8181` | `mvn spring-boot:run -pl local-server` oppure IntelliJ su `LocalServerApplication` |
| Local Server (building-2) | `https://localhost:8182` | `docker-compose -f docker-compose.yml -f docker-compose.multi.yml up local-server-2` |
| Local Server (building-3) | `https://localhost:8183` | `docker-compose -f docker-compose.yml -f docker-compose.multi.yml up local-server-3` |
| Game Client Emulator | — (app desktop JavaFX) | `mvn javafx:run -pl game-client-emulator` oppure IntelliJ su `GameClientEmulatorApplication` |
| MQTT broker building-1 | `tcp://localhost:1883` / `ssl://localhost:8883` | `docker-compose up mqtt-broker-1` |
| MySQL central | `jdbc:mysql://localhost:3306/central_db` | `docker-compose up central-db` |
| MySQL local building-1 | `jdbc:mysql://localhost:3307/local_db` | `docker-compose up local-db-1` |

### 7.3 Dove trovare ogni configurazione

| File | Configura | Modulo |
|---|---|---|
| `central-system/src/main/resources/application.yml` | `server.port` (8180), `server.ssl.*`, `spring.datasource.url`, `jwt.expiration-ms`, `jwt.secret`, `internal.api-key`, `cors.allowed-origins`, `spring.jpa.*`, `app.sync-interval-ms`, `app.server-stale-threshold-ms`, `app.reconciliation-interval-ms` | central-system |
| `local-server/src/main/resources/application.yml` | `server.port` (8181), `server.ssl.*`, `spring.datasource.url`, `mqtt.broker-url`, `app.central-system-url`, `app.local-base-url`, `app.building-id`, `internal.api-key`, `spring.jpa.*`, `app.sync-interval-ms`, `app.server-stale-threshold-ms`, `app.admin.request.timeout-ms`, `app.admin.request.timeout-ms` | local-server |
| `game-client-emulator/src/main/resources/application.yml` | `app.local-server-url` (`https://localhost:8181`), `mqtt.broker-url` (`tcp://localhost:1883`) — **solo documentativo**: il client JavaFX legge da env var | game-client-emulator |
| `game-client-emulator/.../infrastructure/rest/ApiClient.java:49` | `DEFAULT_BASE_URL = "https://localhost:8181"` (costante canonica, override via env `LOCAL_SERVER_URL`) | game-client-emulator |
| `game-client-emulator/.../infrastructure/security/HttpClientHelper.java` | caricamento truststore `local-truststore.p12`, log diagnostico URL+TLS all'avvio | game-client-emulator |
| `game-client-emulator/.../config/MqttClientConfig.java:11` | `DEFAULT_BROKER_URL = "tcp://localhost:1883"` (costante canonica, override via env `MQTT_BROKER_URL`) | game-client-emulator |
| `infrastructure/mosquitto/mosquitto.conf` | `listener 1883` (TCP), `listener 8883` (TLS), `password_file`, `allow_anonymous false` | mosquitto |
| `docker-compose.yml` | porte esposte container: `3306:3306` (central-db), `3307:3306` (local-db-1), `1883:1883`/`8883:8883` (mqtt-broker-1), `8180` (central-system), `8181` (local-server); env var passate ai container (`INTERNAL_API_KEY`, `BUILDING_ID`, `LOCAL_BASE_URL`, `CENTRAL_SYSTEM_URL`, `MQTT_BROKER_URL`, ecc.) | infrastruttura |
| `docker-compose.multi.yml` | override multi-building: `local-db-2` (3308), `local-db-3` (3309), `mqtt-broker-2` (8884), `mqtt-broker-3` (8885), `local-server-2` (8182), `local-server-3` (8183); `BUILDING_ID=building-2`/`building-3` | infrastruttura multi-building |
| `infrastructure/mysql-central/init.sql` | schema DB central (tabelle `users`, `tournaments`, `tournament_standings`, `game_definitions`, `local_admin_buildings`, ecc.) + seed `game_definitions` | DB central |
| `infrastructure/mysql-local/init.sql` / `init-building-2.sql` / `init-building-3.sql` | schema DB local (tabelle `game_sessions`, `reservations`, `tournaments_summary_local`, `tournament_matches_local`, `team_members_local`, `admin_requests_local`, ecc.) + seed `game_catalog` | DB local |
| `infrastructure/tls/generate-certs.ps1` | script generazione keystore/truststore self-signed: SAN include `local-server-1`/`localhost`/`127.0.0.1` | TLS |

### 7.4 Note sulla configurazione

> Le porte `8180`/`8181`/`3306`/`3307`/`1883`/`8883` esposte in `docker-compose.yml` sono **dichiarative** e non vanno modificate (solo documentate). I default delle property in `application.yml` usano l'override via env var (`${VAR:default}`).

> **Game Client Emulator**: non avendo contesto Spring (app JavaFX pura avviata con `Application.launch` in `GameClientApplication`), legge la configurazione **via env var** con `System.getenv().getOrDefault(...)` e non via `application.yml`. I default sono centralizzati in costanti Java: `ApiClient.DEFAULT_BASE_URL` = `https://localhost:8181` (env `LOCAL_SERVER_URL`) e `MqttClientConfig.DEFAULT_BROKER_URL` = `tcp://localhost:1883` (env `MQTT_BROKER_URL`). Il file `game-client-emulator/.../application.yml` ha valore solo documentativo/per Docker.

> La sicurezza TLS (self-signed) si appoggia su due truststore embedded: `central-system-https.p12` (keystore del Central), `local-server-https.p12` (keystore del Local) e `local-truststore.p12` (truststore del client, contiene la RootCA che firma entrambi). I SAN includono `localhost` e `127.0.0.1` per cui la hostname verification di `java.net.http.HttpClient` passa in dev. Per il multi-building occorre estendere la SAN (vedi §8.3).

### 7.5 Come cambiare le porte per evitare conflitti

I default delle porte HTTP dei microservizi (Central System `8180`, Local Server `8181`/`8182`/`8183`) sono definiti nei `application.yml` con override via env var (`${VAR:default}`). Se un'altra applicazione occupa una di queste porte (es. Jenkins/Tomcat su `8080`, oppure un altro tool già su `8180`), applica la procedura **per servizio**. Le porte MySQL (`3306`–`3309`) e MQTT (`1883`/`8883`/`8884`/`8885`) sono **standard di protocollo e non vanno toccate**.

> **Cambio permanente vs temporaneo**: per un cambio **permanente**, modifica il default nel file `application.yml`; per un override **temporaneo** locale, esporta la env var (es. `PORT=9000`) senza toccare il file — la sintassi `${PORT:8180}` usa la env var se presente, altrimenti il default. La porta della mappatura Docker `HOST:CONTAINER` segue la stessa logica: **sinistra** = porta vista dall'host, **destra** = porta interna del container (che deve corrispondere a `${PORT:...}`/`PORT=`). L'`healthcheck` del compose gira **dentro** il container, quindi deve puntare alla porta interna (destra).

#### Central System (porta attuale `8180`)
1. `central-system/src/main/resources/application.yml` → riga `server.port: ${PORT:8180}`: cambia `8180` con la nuova porta, **oppure** avvia con env var `PORT=<nuova>`.
2. `local-server/src/main/resources/application.yml` → riga `app.central-system-url: ${CENTRAL_SYSTEM_URL:https://localhost:8180}`: cambia `8180` (il local-server deve sapere dove trovare il central; va cambiato **anche qui**, non solo lato central), oppure env var `CENTRAL_SYSTEM_URL=https://localhost:<nuova>`.
3. `docker-compose.yml` → blocco `central-system`: riga `ports: "8180:8180"` (host:container) e `healthcheck` `curl ... https://localhost:8180/actuator/health`. Cambia entrambi i lati della mappatura **e** l'URL dell'healthcheck. In `environment:` puoi aggiungere `PORT=<nuova>` oppure affidarti al default del `application.yml` (il central non ha `PORT=` di default nel compose).
4. `game-client-emulator` — il client parla **solo** col Local Server (vedi §7.3); in caso di un eventuale flusso cross-system, verificare `ApiClient.DEFAULT_BASE_URL` e `app.local-server-url` (di norma **non** punta al central: non modificare se non necessario).
5. `infrastructure/tls/generate-certs.ps1` — **SKIP**: i certificati contengono solo SAN (hostname/IP), non porte. Cambiare la porta **non** invalida i certificati self-signed (la porta non compare nel SAN).
6. Test e2e/integration che referenziano `https://localhost:8180` → allinea per coerenza (es. `application-test.yml` `central-system-url: https://central-test:8180`).

#### Local Server — building-1 (porta attuale `8181`)
1. `local-server/src/main/resources/application.yml` → riga `server.port: ${PORT:8181}`: cambia `8181`, **oppure** env var `PORT=<nuova>`.
2. `game-client-emulator/src/main/java/com/gameplatform/client/infrastructure/rest/ApiClient.java:49` → `DEFAULT_BASE_URL = "https://localhost:8181"`: cambia la costante (override via env `LOCAL_SERVER_URL`).
3. `game-client-emulator/src/main/resources/application.yml` → riga `app.local-server-url: ${LOCAL_SERVER_URL:https://localhost:8181}`: cambia `8181`.
4. `local-server/src/main/resources/application.yml` → riga `app.local-base-url: ${LOCAL_BASE_URL:https://local-server-1:8181}`: cambia `8181` (è l'URL che il local-server registra presso il central).
5. `central-system` — il central **non** ha una property che punta all'URL del local: l'URL del local gli arriva a runtime via `POST /internal/servers/register` (auto-registrazione). **Niente da cambiare** lato central.
6. `docker-compose.yml` → blocco `local-server-1`: riga `ports: "8181:8181"`, `environment: - PORT=8181`, `healthcheck` `https://localhost:8181/actuator/health`, e (per i game-client nello stesso compose) `LOCAL_SERVER_URL=https://local-server-1:8181`. Cambia tutte le occorrenze della porta.

#### Local Server — building-2 e building-3 (porte attuali `8182`/`8183`)
Stessa procedura del building-1, ma la configurazione del container vive in `docker-compose.multi.yml`:
- `ports: "8182:8181"` (`local-server-2`) / `"8183:8181"` (`local-server-3`) — la **sinistra** è la porta host (da cambiare se in conflitto), la **destra** è la porta interna del container (letta da `${PORT:...}`/`PORT=` env; per coerenza allinea anche quella);
- `environment: - PORT=8181`, `LOCAL_BASE_URL=https://local-server-2:8181`/`https://local-server-3:8181`, `healthcheck` `https://localhost:8181/...`;
- `LOCAL_SERVER_URL=https://local-server-2:8181`/`https://local-server-3:8181` nei blocchi `game-client-3`/`game-client-4`.

I default in `local-server/src/main/resources/application.yml` (`server.port`, `app.local-base-url`) sono condivisi con building-1: cambiali solo se vuoi spostare **tutti** i building insieme, altrimenti lavora solo sulle env var del `docker-compose.multi.yml`.

> **Nota operativa**: il meccanismo è una **combinazione di default** (`application.yml`) **+ override runtime** (env var `${PORT:...}`). Per un cambio permanente edita il default nel file; per un override temporaneo locale usa la env var. La mappatura Docker `HOST:CONTAINER` segue la stessa regola: sinistra = porta host, destra = porta interna del container (deve corrispondere a `${PORT:...}`/`PORT=`), e l'`healthcheck` del compose usa la porta interna (destra).

---

## 8. Smoke test (Docker)

Procedura manuale per verificare il flusso end-to-end su Docker. Documentata come riferimento per la FASE 4 step 5 (avvio reale) del piano `workflow/analisi/risoluzione_comunicazioni_local_central.md` — non eseguita in CI perché richiede Docker daemon attivo.

### 8.1 Prerequisiti
- Docker + Docker Compose
- Porte libere: 8180, 8181, 1883, 3306, 3307 (vedi §7 per la mappa completa)

### 8.2 Avvio
```bash
cd gamehandler-platform
docker-compose up -d --build central-db local-db-1 mqtt-broker-1
mvn spring-boot:run -pl central-system
# in un altro terminale:
mvn spring-boot:run -pl local-server
```

### 8.3 Verifica (10-15 min di osservazione)
1. **Auto-registrazione**: nel log del local compare `Local server registered with central system`. Nel DB central: `SELECT * FROM central_db.local_servers;` → riga `building-1` con `is_active=1`, `base_url=https://local-server-1:8181`.
2. **Replica utenti**: registra un utente sul local → entro 5 min l'utente appare in `central_db.users`. Il central scheduler replica verso il local → l'utente appare in `local_db.users`.
3. **Outbox**: `SELECT status, COUNT(*) FROM local_db.outbox_events GROUP BY status;` → PENDING → 0, SENT cresce, FAILED → 0 (promosso a DLQ da `OutboxDlqPromotionService`).
4. **Statistiche**: `SELECT * FROM central_db.aggregated_statistics;` → `total_sessions`, `total_aborted_sessions`, `total_reservations` popolati.
5. **Log**: nessun `ERROR` o `WARN` inatteso.

### 8.4 Comandi curl di verifica

La dipendenza `spring-boot-starter-actuator` (runtime) è presente nei `pom.xml` di `central-system` e `local-server`, e in entrambi gli `application.yml` è esposto solo l'endpoint `health` (`management.endpoints.web.exposure.include: health`). Il path `/actuator/health` è inoltre in `permitAll` in entrambi i `SecurityConfig`, quindi i seguenti curl funzionano senza credenziali:

```bash
# Health check central
curl -k https://localhost:8180/actuator/health
# Health check local
curl -k https://localhost:8181/actuator/health
```

> Nota: `curl` è installato anche dentro le immagini Docker (entry `RUN apt-get install -y curl` nei `Dockerfile`), così gli `healthcheck:` del compose possono usare `curl -kfsS https://localhost:818x/actuator/health`.

### 8.5 Scenario — Tournament end-to-end

Scenario smoke manuale che copre l'intero flusso torneo (FASE 4–7) dal punto di vista degli osservatori outbox/DB. Si aggiunge agli step della sezione "Verifica" qui sopra; i prerequisiti sono gli stessi (container `central-db`, `local-db-1`, `mqtt-broker-1` UP, `central-system` e `local-server` avviati). I test di riferimento automatici sono `central-system/src/test/java/com/gameplatform/central/application/service/TournamentFlowEndToEndIT.java` (flusso bracket Central su H2: schedule → `TOURNAMENT_MATCH_COMPLETED` → `advanceWinner` → standings) e `local-server/src/test/java/com/gameplatform/local/application/service/GameSessionServiceTournamentTest.java` (play lato Local: `start`/`end` bindato a `TournamentMatchLocal` → outbox `GAME_SESSION_COMPLETED` + `TOURNAMENT_MATCH_COMPLETED`).

1. **PLATFORM_ADMIN crea torneo** — `POST /api/admin/tournaments` sul local → outbox `TOURNAMENT_CREATE_REQUESTED` (admin-request async). Il Central `SyncEventProcessor` smista il `TOURNAMENT_CREATE_REQUESTED` sul `CreateTournamentUseCase`, poi il `TournamentService.create` emette `TOURNAMENT_SUMMARY_UPSERTED` nello outbox Central. Il replication scheduler propaga il summary al local (`tournaments_summary_local`):
   ```sql
   SELECT event_type, status FROM central_db.outbox_events WHERE event_type IN ('TOURNAMENT_CREATE_REQUESTED','TOURNAMENT_SUMMARY_UPSERTED');
   SELECT * FROM local_db.tournaments_summary_local;
   ```
2. **PLATFORM_ADMIN schedule** — `POST /api/admin/tournaments/{id}/schedule` → outbox `TOURNAMENT_SCHEDULE_REQUESTED`. Il Central `ScheduleTournamentMatchesUseCase` genera il bracket single-elimination round-1 e, per ogni match, emette `TOURNAMENT_MATCH_SCHEDULED` (replicato al local della building coinvolta → `tournament_matches_local`):
   ```sql
   SELECT * FROM central_db.tournament_matches WHERE round=1;
   SELECT * FROM local_db.tournament_matches_local WHERE status='SCHEDULED';
   ```
3. **PLAYER iscrizione** — `POST /api/tournaments/{id}/participants` → outbox `PARTICIPANT_REGISTER_REQUESTED` (admin-request async con role pre-check `PLAYER`). Il Central `RegisterTournamentParticipantUseCase` registra il partecipante e emette `TOURNAMENT_PARTICIPANTS_UPSERTED` (replicato al local → `tournament_participants_local`). Per i tornei team-based il Central emette anche `TEAM_MEMBERS_UPSERTED` (replicato → `team_members_local`):
   ```sql
   SELECT * FROM local_db.tournament_participants_local;
   SELECT * FROM local_db.team_members_local;
   ```
4. **PLAYER `startMatch`** — `POST /api/tournaments/{id}/matches/{matchId}/start` (o comando client MQTT) → `GameSessionService.start(...tournamentMatchId)` valida lo stato `SCHEDULED`, flip del `tournament_matches_local` a `IN_PROGRESS`, crea la `GameSession` e pubblica l'evento di inizio sul topic MQTT `building/{buildingId}/game/{gameId}/session/start` (segnale `GAME_SESSION_STARTED`):
   ```bash
   mosquitto_sub -h localhost -p 1883 -t 'building/building-1/game/+/session/start' -u <user> -P <pw>
   ```
   ```sql
   SELECT status, tournament_match_id FROM local_db.game_sessions WHERE tournament_match_id IS NOT NULL;
   ```
5. **PLAYER `endMatch` (winner dichiarato)** — `endMatch` completa la `GameSession` (winner non nullo per i match torneo) ed emette atomicamente nello outbox Local **due** righe: `GAME_SESSION_COMPLETED` (statistiche aggregate) e `TOURNAMENT_MATCH_COMPLETED` (payload `TournamentMatchResultDto`); il `tournament_matches_local` passa a `COMPLETED`. Per tornei team-based la `GameSessionService.end` costruisce un `TeamResult` (winner = TeamId), serializzato come subtype `"TEAM"` nel payload MQTT. Il Central `SyncEventProcessor.handleTournamentMatchCompleted` invoca `TournamentBracketService.advanceWinner` che popola il parent di round successivo (emettendo un nuovo `TOURNAMENT_MATCH_SCHEDULED`) o, all'ultima round, porta il `Tournament` a `COMPLETED` e assegna i rank via `TournamentStandingsService`:
   ```sql
   SELECT event_type, status FROM local_db.outbox_events WHERE event_type IN ('GAME_SESSION_COMPLETED','TOURNAMENT_MATCH_COMPLETED');
   SELECT * FROM central_db.tournament_matches WHERE status='SCHEDULED' AND round>1;
   SELECT status FROM central_db.tournaments;
   SELECT * FROM central_db.tournament_standings ORDER BY `rank`;
   ```

> Latenza attesa: ciascuno step admin/PLAYER attraversa il pattern outbox async (scheduler con `app.sync-interval-ms`, default 5 min in produzione); per lo smoke impostare `app.sync-interval-ms` basso (es. `10000`) o richiamare manualmente i scheduler per accelerare la propagazione.

### 8.6 Smoke test multi-building

Avvio della composizione multi-building (building-1 da `docker-compose.yml` + building-2 e building-3 dall'override):
```bash
docker compose -f docker-compose.yml -f docker-compose.multi.yml up
```

Override di ambiente raccomandati per uno smoke run di 15 minuti (evita flapping del health-monitor e rumore di re-push di reconciliation):
- `SERVER_STALE_THRESHOLD_MS=3600000` (1 ora — impedisce al `LocalServerHealthMonitorService` di disattivare i building durante lo smoke)
- `RECONCILIATION_INTERVAL_MS` lasciato al default di 1 ora OPPURE impostato alto per silenziare i log INFO di `UserReplicationReconciliationService`

La composizione provisiona `local-db-2`/`local-db-3` (porte host 3308/3309) con `init-building-2.sql`/`init-building-3.sql`, `mqtt-broker-2`/`mqtt-broker-3` (porte host 8884/8885 — broker separati per isolamento di namespace perché la `mosquitto.conf` di base non ha ACL), e `local-server-2`/`local-server-3` con `BUILDING_ID=building-2`/`building-3`.

#### Scenari smoke (coperti anche da `MultiBuildingEndToEndIT`)
1. building-2 + building-3 self-register → entrambe le righe in `local_servers`.
2. `USER_REGISTERED` su building-2 → replicato verso building-1 E building-3 (due righe in `replication_progress`; event SENT).
3. `GAME_SESSION_COMPLETED` per building-2/CHESS e building-3/FOOSBALL → due righe distinte in `aggregated_statistics`, nessuna contaminazione cross-building.
4. re-invio dello stesso `USER_REGISTERED` da building-2 → nessun secondo push (dedup via `processed_events`).

#### Note operative
- **TLS SAN**: `infrastructure/tls/generate-certs.ps1` attualmente include nella SAN solo `local-server-1`/`localhost`/`127.0.0.1` (vedi `local-server-https.ext`). Lo smoke multi-building richiede pertanto di estendere lo script per aggiungere `local-server-2`/`local-server-3` alla SAN; SAN mancanti causano failure di handshake TLS tra central e i nuovi local server.
- **Namespace MQTT**: ogni building usa il proprio broker (`mqtt-broker-2`/`mqtt-broker-3`) così i path dei topic come `building/bld-1/...` e `building/bld-2/...` sono fisicamente separati (i broker non condividono stato).
- **Heartbeat**: inviare un `POST /internal/servers/register` da ogni building entro la finestra `SERVER_STALE_THRESHOLD_MS` per mantenere `is_active=true` (la build è auto-registrata allo startup; la guida heartbeat è per smoke run lunghi che superano la soglia).
