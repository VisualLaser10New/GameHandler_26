# Implementazione IoT — GameHandler_26: Boardgame Platform

> **Versione:** 1.1 | **Data:** 2026-07-14 | **Corso:** PISSIR, 3° anno
>
> Guida pratica per sviluppatori, DevOps e contributor. Copre setup locale, build, test, debugging, deployment, contribuzione al progetto e **integrazione futura con dispositivi IoT (tavoli fisici)**.

---

## Indice

1. [Setup Ambiente di Sviluppo](#1-setup-ambiente-di-sviluppo)
2. [Build e Esecuzione Locale](#2-build-e-esecuzione-locale)
3. [Struttura del Repository](#3-struttura-del-repository)
4. [Code Style e Convenzioni](#4-code-style-e-convenzioni)
5. [Testing](#5-testing)
6. [Debugging](#6-debugging)
7. [Database](#7-database)
8. [API Playground](#8-api-playground)
9. [CI/CD](#9-cicd)
10. [Monitoring e Logging](#10-monitoring-e-logging)
11. [Troubleshooting Comune](#11-troubleshooting-comune)
12. [Contribuzione](#12-contribuzione)
13. [FASE 7 — Utenti, Tornei e Dashboard multi-edificio](#13-fase-7--utenti-tornei-e-dashboard-multi-edificio)
14. [Integrazione futura con dispositivi IoT (tavoli fisici)](#14-integrazione-futura-con-dispositivi-iot-tavoli-fisici)
    - 14.1 [Architettura attuale: Hub & Spoke](#141-architettura-attuale-hub--spoke)
    - 14.2 [Login multi-giocatore e identità del client](#142-login-multi-giocatore-e-identità-del-client)
    - 14.3 [Istanze client e concetto di building](#143-istanze-client-e-concetto-di-building)
    - 14.4 [Catalogo giochi e lista in GUI emulator](#144-catalogo-giochi-e-lista-in-gui-emulator)
    - 14.5 [Evoluzione del concetto di lobby con tavoli IoT fisici](#145-evoluzione-del-concetto-di-lobby-con-tavoli-iot-fisici)
    - 14.6 [Tornei su tavoli fisici](#146-tornei-su-tavoli-fisici)
    - 14.7 [Componenti mancanti per l'integrazione IoT reale](#147-componenti-mancanti-per-lintegrazione-iot-reale)

---

## 1. Setup Ambiente di Sviluppo

### 1.1 Prerequisiti

| Strumento | Versione | Note |
|---|---|---|
| **IntelliJ IDEA** | Community o Ultimate | Scarica da [jetbrains.com](https://www.jetbrains.com/idea/) |
| **Docker Desktop** | >= 4.x | Scarica da [docker.com](https://desktop.docker.com/win/main/amd64/Docker%20Desktop%20Installer.exe) |
| **Git** | >= 2.x | Incluso in WSL o Git for Windows |
| **Java 21** | Eclipse Temurin o Amazon Corretto | **Non installare manualmente**: vedi Step 1 |
| **Maven** | Embedded in IntelliJ | Non richiesto separatamente |

> Java 21 e Maven sono gestiti da IntelliJ IDEA. Non è necessario installarli nel sistema operativo.

### 1.2 Step 1 — Configurare Java 21 in IntelliJ

1. Aprire IntelliJ IDEA e caricare la cartella `gamehandler-platform` (quella con il `pom.xml` radice).
2. Attendere che IntelliJ indicizzi il progetto Maven (barra di progresso in basso a destra).
3. Aprire **File > Project Structure** (`Ctrl+Alt+Shift+S`).
4. In **Project Settings > Project**, sotto **SDK**, cliccare su **Download JDK...**.
5. Selezionare **Versione: 21**, **Vendor: Eclipse Temurin** (o Amazon Corretto).
6. Cliccare **Download** → **Apply**.

### 1.3 Step 2 — Inizializzare l'infrastruttura Docker

Prima di avviare qualsiasi servizio Java, avviare i container di infrastruttura:

```bash
# Dalla directory gamehandler-platform/
docker-compose up -d central-db local-db-1 mqtt-broker-1
```

Questo avvia:
- `central-db` — MySQL 8.0 per il Central System, porta **3306**
- `local-db-1` — MySQL 8.0 per il Local Server, porta **3307**
- `mqtt-broker-1` — Eclipse Mosquitto 2.0, porte **8883** (TLS) e **1883** (plain)

Verificare lo stato:
```bash
docker-compose ps
```

Per fermare l'infrastruttura:
```bash
docker-compose down
```

Per eliminare anche i dati (reset completo):
```bash
docker-compose down -v
```

### 1.4 Step 3 — Variabili d'ambiente per sviluppo locale

Creare un file `.env` nella cartella `gamehandler-platform/` (già presente nel `.gitignore`):

```dotenv
# .env — NON committare questo file
CENTRAL_DB_PASSWORD=root
LOCAL_DB_PASSWORD=root
INTERNAL_API_KEY=dev-secret-key-change-in-production
GAME_CLIENT_MQTT_PASSWORD=foosball_password
```

> **Sicurezza:** Non committare mai il file `.env`. In produzione usare un secret manager o variabili d'ambiente del sistema operativo.

### 1.5 Configurazione `application.yml` per sviluppo locale

I microservizi Spring Boot hanno profili di sviluppo che puntano a `localhost`. Verificare che i file `application.yml` contengano:

**Central System** — punta a `localhost:3306`:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/central_db
    username: root
    password: root
```

**Local Server** — punta a `localhost:3307` e porta MQTT plain 1883 per dev:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3307/local_db
    username: root
    password: root
app:
  mqtt-broker-url: tcp://localhost:1883   # Porta plain per sviluppo
  building-id: building-1
  central-system-url: http://localhost:8080
  sync-interval-ms: 300000
  healthcheck-interval-ms: 300000
```

> In sviluppo locale si usa la porta MQTT **1883** (plain, senza TLS) configurata con `allow_anonymous true`. Il TLS mTLS è attivo **solo in produzione** (porta 8883).

---

## 2. Build e Esecuzione Locale

### 2.1 Approccio Ibrido (raccomandato per sviluppo)

L'approccio consigliato per il team è il seguente:
- **Docker**: solo per i database e il broker MQTT
- **IntelliJ**: per compilare ed eseguire i microservizi Spring Boot in modalità nativa

**Vantaggi:** hot-reload, debugger Java nativo, nessun rebuild Docker per ogni modifica.

#### Avvio dei microservizi in IntelliJ

1. Assicurarsi che i container Docker siano attivi (`docker-compose ps`).
2. Trovare le classi main:
   - `CentralSystemApplication.java` in `central-system`
   - `LocalServerApplication.java` in `local-server`
   - `GameClientApplication.java` in `game-client-emulator`
3. Cliccare sul pulsante **Play** (▶) accanto alla classe main in IntelliJ.
4. I servizi si avviano e si connettono ai container Docker in background.

#### Ordine di avvio consigliato

```
1. docker-compose up -d central-db local-db-1 mqtt-broker-1
2. CentralSystemApplication  (attende il DB)
3. LocalServerApplication     (attende DB + MQTT + Central)
4. GameClientApplication      (attende MQTT + Local)
```

### 2.2 Ambiente di Produzione (Docker completo)

Per simulare l'ambiente di produzione o per la consegna all'esame:

```bash
# 1. Fermare eventuali istanze in IntelliJ

# 2. Compilare tutti i moduli
cd gamehandler-platform/
mvn clean package -DskipTests

# 3. Avviare tutto lo stack
docker-compose up -d --build

# 4. Monitorare i log in tempo reale
docker-compose logs -f

# 5. Spegnere tutto
docker-compose down
```

> Il flag `--build` ricompila le immagini Docker dai `Dockerfile` di ogni microservizio.

### 2.3 Build dei soli moduli shared

```bash
# Costruisce e installa solo i moduli condivisi nel repository Maven locale
mvn clean install -pl shared/shared-domain,shared/shared-dto,shared/shared-mqtt
```

### 2.4 Compilazione di un singolo modulo

```bash
# Esempio: rebuild del solo local-server
mvn clean package -pl local-server -am -DskipTests
```

Il flag `-am` (also-make) compila automaticamente anche le dipendenze upstream.

---

## 3. Struttura del Repository

```
gamehandler-platform/
├── pom.xml                        ← Parent POM (versioni, dipendenze globali)
├── docker-compose.yml             ← Stack completo (produzione)
├── README.md                      ← Guida setup (manuale sviluppatore)
│
├── shared/                        ← Librerie senza dipendenze framework
│   ├── shared-domain/             ← Value Object, interfacce, GameResult, DomainEvents
│   ├── shared-dto/                ← Contratti REST/MQTT (Java record)
│   └── shared-mqtt/               ← Topic MQTT, payload, serializzatore
│
├── central-system/                ← Microservizio Spring Boot (porta 8080)
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/gameplatform/central/
│       │   ├── domain/            ← Model, ports/in, ports/out, exception
│       │   ├── application/       ← Use Case services
│       │   └── infrastructure/    ← REST adapters, JPA, security, config
│       └── test/java/com/gameplatform/central/
│           ├── application/service/   ← Unit test dei service
│           ├── domain/model/          ← Unit test delle entità di dominio
│           └── infrastructure/        ← Test controller, filter, mapper
│
├── local-server/                  ← Microservizio Spring Boot (porta 8080/8081)
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/gameplatform/local/
│       │   ├── domain/            ← Model, ports/in, ports/out, exception
│       │   ├── application/       ← Use Case services
│       │   └── infrastructure/    ← REST, MQTT adapters, security, config
│       └── test/                  ← [DA CHIARIRE: test presenti nel local-server?]
│
├── game-client-emulator/          ← JavaFX client
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/gameplatform/client/
│       ├── domain/                ← GameLifecycle, GameFactory, ClientState, games/
│       ├── application/           ← GameOrchestrationService, HeartbeatService...
│       └── infrastructure/        ← MQTT, UI JavaFX, security (CSR), config
│
└── infrastructure/
    ├── mysql-central/init.sql     ← DDL schema database centrale
    ├── mysql-local/init.sql       ← DDL schema database locale
    ├── mosquitto/
    │   ├── mosquitto.conf         ← Configurazione broker
    │   ├── certs/                 ← Certificati TLS (NON versionare chiavi private)
    │   └── password_file          ← Credenziali MQTT plain (solo dev)
    └── tls/                       ← Certificati HTTPS per i microservizi
```

---

## 4. Code Style e Convenzioni

### 4.1 Naming

| Elemento | Convenzione | Esempio |
|---|---|---|
| Classi | PascalCase | `GameSessionService`, `UserJpaEntity` |
| Interfacce Use Case | PascalCase + `UseCase` suffix | `StartGameSessionUseCase` |
| Interfacce Repository | PascalCase + `Repository`/`Port` suffix | `GameSessionRepository`, `PublishGameStatePort` |
| Adapter JPA | PascalCase + `RepositoryAdapter` suffix | `GameSessionRepositoryAdapter` |
| Entità JPA | PascalCase + `JpaEntity` suffix | `GameSessionJpaEntity` |
| Mapper | PascalCase + `Mapper` suffix | `GameSessionMapper` |
| Controller | PascalCase + `Controller` suffix | `GameSessionController` |
| DTO | PascalCase + `Dto` suffix | `GameSessionDto` (Java record) |
| Value Object | PascalCase senza suffix | `UserId`, `GameId`, `BuildingId` |
| Test class | Nome classe + `Test` suffix | `AuthServiceTest` |
| Costanti | UPPER_SNAKE_CASE | `BATCH_SIZE`, `USER_REGISTERED_EVENT` |

### 4.2 Package structure (Clean Architecture)

Ogni microservizio segue rigidamente questo schema di package:

```
com.gameplatform.{central|local}/
├── domain/
│   ├── model/         ← Classi di dominio pure (no annotation framework)
│   ├── ports/
│   │   ├── in/        ← Interfacce Use Case (entry point per l'application layer)
│   │   └── out/       ← Interfacce Repository/Port (implementate dall'infrastructure)
│   └── exception/     ← Eccezioni di dominio tipizzate
├── application/
│   └── service/       ← Implementazioni dei Use Case
└── infrastructure/
    ├── adapters/
    │   ├── in/
    │   │   ├── rest/  ← @RestController
    │   │   └── mqtt/  ← Listener MQTT
    │   └── out/
    │       ├── mysql/ ← entity/, repository/, adapter/, mapper/
    │       ├── rest/  ← Client HTTP verso altri servizi
    │       └── mqtt/  ← Publisher MQTT
    ├── config/        ← @Configuration classes
    └── security/      ← Filter, TokenProvider, etc.
```

> **Regola fondamentale:** Le classi nel `domain/` non devono mai importare `org.springframework.*`, `jakarta.persistence.*` o qualsiasi altra dipendenza framework.

### 4.3 Uso dei Java Record

I DTO (`shared-dto`) e i Value Object (`shared-domain/model`) sono implementati come Java record per garantire immutabilità e concisione:

```java
// CORRETTO — DTO come record
public record LoginRequestDto(
    @NotBlank String username,
    @NotBlank String password
) {}

// CORRETTO — Value Object come record
public record UserId(String value) {}
```

### 4.4 Transazioni e MQTT

- I service dell'`application/` usano `@Transactional` a livello di classe o metodo.
- La pubblicazione MQTT avviene sempre **dopo il commit** della transazione tramite `TransactionSynchronizationManager`. Non pubblicare mai in MQTT all'interno di una transazione attiva.

```java
// Pattern corretto per publish post-commit [fonte: GameSessionService.java]
if (TransactionSynchronizationManager.isActualTransactionActive()) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
            mqttPort.publishState(gameId, status);
        }
    });
} else {
    mqttPort.publishState(gameId, status);
}
```

### 4.5 Logging

- Framework: **SLF4J + Logback** (default Spring Boot).
- Logger: `private static final Logger log = LoggerFactory.getLogger(NomeClasse.class);`
- Livelli: `ERROR` per errori critici, `WARN` per anomalie recuperabili, `INFO` per operazioni normali, `DEBUG` per dettagli.
- Non loggare mai password, token JWT o chiavi private.

### 4.6 Eccezioni

- Le eccezioni di dominio estendono `RuntimeException` e risiedono in `domain/exception/`.
- Il `GlobalExceptionHandler` (`@RestControllerAdvice`) mappa le eccezioni in risposte HTTP; i controller non devono contenere `try-catch` per la gestione degli errori HTTP.

---

## 5. Testing

### 5.1 Test class disponibili (central-system)

| File di test | Layer | Framework | Cosa testa |
|---|---|---|---|
| `AuthServiceTest` | Application | JUnit 5 + Mockito | Rate limiting, timing attack, login corretto/errato |
| `SyncReceiverServiceTest` | Application | JUnit 5 + Mockito | Idempotenza eventi, aggiornamento statistiche |
| `SyncReceiverServiceBugTest` | Application | JUnit 5 + Mockito | Regressioni bug noti |
| `UserServiceTest` | Application | JUnit 5 + Mockito | Registrazione, aggiornamento utenti |
| `UserReplicationSchedulerServiceTest` | Application | JUnit 5 + Mockito | Batch replication, ReplicationProgress |
| `SharedCentralSystemCompatibilityTest` | Cross-module | JUnit 5 | Compatibilità shared-dto con central-system |
| `AuthControllerTest` | Infrastructure (REST) | JUnit 5 + MockMvc | Routing, request/response, auth |
| `UserControllerTest` | Infrastructure (REST) | JUnit 5 + MockMvc | Registrazione utenti via HTTP |
| `StatisticsControllerTest` | Infrastructure (REST) | JUnit 5 + MockMvc | Accesso statistiche globali |
| `SyncControllerTest` | Infrastructure (REST) | JUnit 5 + MockMvc | Endpoint sync interno |
| `JwtTokenProviderTest` | Infrastructure (Security) | JUnit 5 | JWT generation/validation |
| `JwtAuthenticationFilterTest` | Infrastructure (Security) | JUnit 5 + Mockito | Filtro JWT |
| `InternalApiKeyFilterTest` | Infrastructure (Security) | JUnit 5 + Mockito | Filtro API Key |
| `StatisticsMapperTest` | Infrastructure (Mapper) | JUnit 5 | Correttezza mapping JPA ↔ domain |
| `LocalServerRestAdapterTest` | Infrastructure (REST client) | JUnit 5 + Mockito | HTTP client adapter |

**Stato:** Test presenti e documentati solo per `central-system`. [DA CHIARIRE: esistenza di test nel `local-server` e nel `game-client-emulator`]

### 5.2 Eseguire i test

```bash
# Tutti i test del progetto
mvn test

# Solo un modulo specifico
mvn test -pl central-system

# Un singolo test class
mvn test -pl central-system -Dtest=AuthServiceTest

# Un singolo metodo di test
mvn test -pl central-system -Dtest=AuthServiceTest#authenticate_shouldReturnLoginResponse_whenCredentialsAreValid

# Skip dei test (build veloce)
mvn clean package -DskipTests
```

### 5.3 Pattern di test unit (Mockito)

```java
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private FailedLoginAttemptRepository failedLoginAttemptRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
            userRepository,
            failedLoginAttemptRepository,
            jwtTokenProvider,
            Clock.systemUTC()
        );
        lenient().when(failedLoginAttemptRepository.countFailedAttempts(anyString(), any()))
                 .thenReturn(0L);
    }

    @Test
    void authenticate_shouldReturnLoginResponse_whenCredentialsAreValid() { ... }
}
```

**Convenzioni:**
- Nessun `@SpringBootTest` nei test unitari: si usa `MockitoExtension` per non caricare il contesto Spring.
- Il `Clock` è iniettato come dipendenza nei service (non chiamato staticamente) per permettere test deterministici.
- I test usano AssertJ (`assertThat`, `assertThatThrownBy`) per assertion leggibili.

### 5.4 Pattern di test controller (MockMvc)

```java
@WebMvcTest(AuthController.class)
@Import(TestSecurityConfig.class)  // Disabilita la security reale per i test
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean AuthenticateUserUseCase authenticateUserUseCase;

    @Test
    void login_shouldReturn200_whenCredentialsAreValid() throws Exception {
        when(authenticateUserUseCase.authenticate("alice", "pass"))
            .thenReturn(new LoginResponseDto("token", "id", Instant.now().plusSeconds(3600)));

        mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"alice\",\"password\":\"pass\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("token"));
    }
}
```

### 5.5 Coverage

[DA CHIARIRE: nessun plugin di coverage (JaCoCo) è configurato nel pom.xml corrente. I test coprono principalmente la business logic del `central-system`. Il `local-server` e il `game-client-emulator` non hanno test automatizzati documentati.]

---

## 6. Debugging

### 6.1 Avvio in modalità debug in IntelliJ

1. Cliccare sul pulsante **Debug** (🐛) invece di **Run** (▶) sulla classe main.
2. IntelliJ si collega automaticamente alla JVM con il debugger JDWP.
3. Impostare breakpoint con `F9` sulle righe di interesse.

### 6.2 Breakpoint strategici

| Problema da investigare | Breakpoint consigliato |
|---|---|
| Login fallisce inspiegabilmente | `AuthService.authenticate()` — riga `BCrypt.checkpw()` |
| Rate limit scattato inaspettatamente | `AuthService.checkRateLimit()` — riga `if (failures >= 5)` |
| Evento outbox non inviato | `SyncSchedulerService.syncWithCentral()` — riga `if (!isReachable())` |
| Sessione non avviata (MQTT) | `GameSessionService.start()` — riga `findActiveByGameId()` |
| Heartbeat non registrato | `HeartbeatListener.handleHeartbeat()` — branch `"ack".equals(leaf)` |
| Timeout sessione non triggered | `HealthCheckService.performHealthCheck()` — riga `if (missed >= 3)` |
| CSR signing fallisce | `DeviceRegistrationController.registerDevice()` — riga `signCsr()` |
| JWT non valido | `JwtTokenProvider.validateToken()` + `JwtAuthenticationFilter` |

### 6.3 Log utili

```bash
# Log Central System in Docker
docker-compose logs -f central-system

# Log Local Server in Docker
docker-compose logs -f local-server-1

# Log Mosquitto
docker-compose logs -f mqtt-broker-1

# Filtrare solo WARN e ERROR
docker-compose logs -f central-system 2>&1 | grep -E "WARN|ERROR"
```

**In IntelliJ:** I log appaiono nella console Run/Debug. Usare il filtro della console per cercare stringhe specifiche (es. "Sync payload", "Failed login", "missed").

### 6.4 Ispezionare i messaggi MQTT

In modalità sviluppo (porta 1883, plain), usare **MQTT Explorer** o `mosquitto_sub`:

```bash
# Iscriversi a tutti i topic dell'edificio 1
mosquitto_sub -h localhost -p 1883 -t "building/building-1/#" -v

# Pubblicare un heartbeat ack manuale per test
mosquitto_pub -h localhost -p 1883 -t "building/building-1/game/game-foosball-1/heartbeat/ack" -m "{}"
```

### 6.5 Ispezione del database

```bash
# Connettersi al DB centrale
docker exec -it gamehandler-platform-central-db-1 mysql -u root -proot central_db

# Connettersi al DB locale
docker exec -it gamehandler-platform-local-db-1-1 mysql -u root -proot local_db

# Query utili
SELECT * FROM outbox_events WHERE status = 'PENDING';
SELECT * FROM game_sessions ORDER BY created_at DESC LIMIT 10;
SELECT * FROM processed_events ORDER BY processed_at DESC LIMIT 10;
SELECT * FROM local_servers;
SELECT * FROM failed_login_attempts ORDER BY created_at DESC LIMIT 20;
```

---

## 7. Database

### 7.1 Schema

Gli script DDL si trovano in:
- `infrastructure/mysql-central/init.sql` — Schema del Central System
- `infrastructure/mysql-local/init.sql` — Schema del Local Server

Entrambi vengono eseguiti automaticamente da Docker al primo avvio del container (via `docker-entrypoint-initdb.d`).

### 7.2 Connessioni

| Ambiente | DB | Host | Porta | Database | User | Password |
|---|---|---|---|---|---|---|
| Docker (interno) | Central | `central-db` | 3306 | `central_db` | `root` | `${CENTRAL_DB_PASSWORD}` |
| Host (sviluppo) | Central | `localhost` | 3306 | `central_db` | `root` | `root` |
| Docker (interno) | Local | `local-db-1` | 3306 | `local_db` | `root` | `${LOCAL_DB_PASSWORD}` |
| Host (sviluppo) | Local | `localhost` | 3307 | `local_db` | `root` | `root` |

> La porta `3307` sul host è il forward di `local-db-1:3306` definito nel `docker-compose.yml`.

### 7.3 Reset del database

```bash
# Reset completo (elimina volumi Docker - tutti i dati persi)
docker-compose down -v
docker-compose up -d central-db local-db-1 mqtt-broker-1
# I container riapplicano init.sql automaticamente al primo start
```

### 7.4 Migrazioni schema

[DA CHIARIRE: non è configurato un tool di migrazione schema (Flyway/Liquibase). Le modifiche allo schema vanno applicate manualmente ai file `init.sql` e richiedono un reset del database in sviluppo. In produzione, le ALTER TABLE devono essere eseguite manualmente prima del deploy.]

### 7.5 Popolare dati di test iniziali

Il catalogo dei giochi (`game_catalog`) deve essere popolato manualmente o tramite script SQL prima di avviare i game-client. Esempio:

```sql
-- Inserire giochi nel DB locale (eseguire su local_db)
INSERT INTO game_catalog (id, game_type, name, building_id, status)
VALUES
  ('game-foosball-1', 'FOOSBALL', 'Calciobalilla 1', 'building-1', 'AVAILABLE'),
  ('game-chess-1',    'CHESS',    'Scacchi 1',        'building-1', 'AVAILABLE');
```

> Il `DeviceRegistrationController` verifica che il `gameId` esista nel catalogo prima di firmare un CSR. Senza questo inserimento, la registrazione del device restituisce HTTP 403.

---

## 8. API Playground

### 8.1 Swagger / OpenAPI

[DA CHIARIRE: il `pom.xml` del `central-system` e del `local-server` **non includono** la dipendenza `springdoc-openapi`. Non è disponibile un'interfaccia Swagger UI. Per aggiungere Swagger:]

```xml
<!-- Da aggiungere in central-system/pom.xml e local-server/pom.xml -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

Dopo l'aggiunta, l'UI sarebbe disponibile a `https://localhost:8080/swagger-ui.html`.

### 8.2 Test manuale con curl

#### Login su Central System

```bash
curl -k -X POST https://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password"}' | python -m json.tool
```

> Il flag `-k` disabilita la verifica TLS (utile in sviluppo con certificati self-signed).

#### Registrazione utente

```bash
curl -k -X POST https://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"SecurePass123","email":"alice@example.com"}'
```

#### Invio sync payload (simulare un Local Server)

```bash
curl -k -X POST https://localhost:8080/internal/sync/receive \
  -H "Content-Type: application/json" \
  -H "X-Internal-Api-Key: dev-secret-key-change-in-production" \
  -d '{
    "buildingId": "building-1",
    "events": [{
      "eventId": "evt-001",
      "eventType": "GAME_SESSION_COMPLETED",
      "payload": "{\"gameType\":\"FOOSBALL\",\"durationSeconds\":300}",
      "createdAt": "2026-01-01T10:05:00Z"
    }]
  }'
```

#### Creare una prenotazione (Local Server)

```bash
TOKEN="<JWT ottenuto dal login>"
curl -k -X POST https://localhost:8081/api/reservations \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"gameId":"game-foosball-1","startTime":"2026-01-01T14:00:00Z","endTime":"2026-01-01T15:00:00Z"}'
```

#### Lista giochi disponibili

```bash
TOKEN="<JWT>"
curl -k -X GET https://localhost:8081/api/games/available \
  -H "Authorization: Bearer $TOKEN"
```

### 8.3 Postman

[DA CHIARIRE: non è presente una collection Postman nel repository. Si raccomanda di crearla e versionarla in `infrastructure/postman/` per il team.]

---

## 9. CI/CD

[DA CHIARIRE: non è configurata nessuna pipeline CI/CD nel repository (nessun file `.github/workflows/`, `.gitlab-ci.yml`, `Jenkinsfile` o equivalente).]

**Proposta per una pipeline minimale (GitHub Actions):**

```yaml
# .github/workflows/build.yml
name: Build & Test
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Build e test
        run: mvn clean verify
        working-directory: gamehandler-platform
```

**Proposta per pipeline di produzione:**

1. `mvn clean verify` — build + test
2. `docker build` — costruisce le immagini
3. `docker push` — pusha su registry (es. Docker Hub o GitHub Container Registry)
4. Deploy tramite `docker-compose pull && docker-compose up -d`

---

## 10. Monitoring e Logging

### 10.1 Logging (Implementato)

**Framework:** SLF4J + Logback (incluso in Spring Boot, nessuna configurazione aggiuntiva).

**Livelli di log per package** (configurazione predefinita Spring Boot):

| Package | Livello consigliato produzione |
|---|---|
| `com.gameplatform` | `INFO` |
| `org.springframework.security` | `WARN` |
| `org.hibernate.SQL` | `WARN` (abilitare `DEBUG` solo in sviluppo) |

**Log significativi prodotti dal codice:**

```
# AuthService — login fallito
WARN  c.g.c.a.s.AuthService - Failed login attempt: Incorrect password for username 'alice'
WARN  c.g.c.a.s.AuthService - Rate limit blocked: Username 'eve' has had 5 failed login attempts

# SyncReceiverService — evento deduplicato
INFO  c.g.c.a.s.SyncReceiverService - Duplicate sync event caught, skipping: evt-001

# UserReplicationSchedulerService — replica fallita
ERROR c.g.c.a.s.UserReplicationSchedulerService - Failed to push user event [evt-002] to server [https://local-server-2:8080]

# CentralSystemRestAdapter — sync
WARN  c.g.l.i.a.o.r.CentralSystemRestAdapter - Central system is unreachable at https://central-system:8080
INFO  c.g.l.i.a.o.r.CentralSystemRestAdapter - Sync payload sent successfully to central system
```

### 10.2 Health Check

Il Local Server implementa un `HealthCheckService` che verifica periodicamente i client tramite heartbeat MQTT. È inoltre ora esposto l'endpoint standard `/actuator/health` di Spring Boot Actuator su entrambi i microservizi.

La dipendenza `spring-boot-starter-actuator` (scope runtime) è stata aggiunta ai `pom.xml` di `central-system` e `local-server`. In entrambi gli `application.yml` è stato configurato `management.endpoints.web.exposure.include: health` (solo `health`, per mantenere minima la superficie esposta) e `/actuator/health` è in `permitAll` in entrambi i `SecurityConfig`. `curl` è installato dentro entrambe le immagini Docker, così i blocchi `healthcheck:` del `docker-compose.yml` (e del `docker-compose.multi.yml`) usano `curl -kfsS https://localhost:808x/actuator/health`; le condizioni `depends_on: service_healthy` sui DB fanno sì che i server partano solo a DB sano.

### 10.3 Metriche

[DA CHIARIRE: non è integrato nessun sistema di metriche (Micrometer, Prometheus, Grafana). Le statistiche di utilizzo sono aggregate nella tabella `aggregated_statistics` del Central System, ma non sono esposte in formato time-series.]

### 10.4 Alerting MQTT

Il Local Server pubblica alert MQTT al topic `building/{buildingId}/alerts` nei seguenti casi:
- Client non raggiungibile dopo 3 cicli di heartbeat mancati (15 minuti)

```json
{
  "type": "UNREACHABLE",
  "gameId": "game-foosball-1",
  "message": "Client has missed 3 consecutive heartbeat cycles (15 minutes). Declaring unreachable.",
  "timestamp": "2026-01-01T10:15:00Z"
}
```

---

## 11. Troubleshooting Comune

### 11.1 `Connection refused` su MySQL all'avvio di Spring Boot

**Sintomo:** `com.mysql.cj.exceptions.CommunicationsException: Communications link failure`

**Causa:** Il container Docker del database non è ancora avviato o è in fase di inizializzazione.

**Soluzione:**
```bash
docker-compose ps                    # Verificare che central-db o local-db-1 sia "Up"
docker-compose logs central-db       # Controllare eventuali errori di init
# Attendere ~10 secondi e riavviare il microservizio Spring Boot
```

---

### 11.2 MQTT connection refused in produzione (porta 8883)

**Sintomo:** `javax.net.ssl.SSLHandshakeException` o `Connection refused 8883`

**Causa:** I certificati TLS non sono presenti nella cartella `infrastructure/tls/` o `infrastructure/mosquitto/certs/`.

**Soluzione:**
```bash
# Verificare la presenza dei certificati
ls infrastructure/tls/
ls infrastructure/mosquitto/certs/

# In sviluppo, usare la porta 1883 (plain) modificando MQTT_BROKER_URL
# nel docker-compose.yml o in application.yml
```

---

### 11.3 HTTP 403 su `/api/devices/register`

**Sintomo:** `{"error": "Device is not pre-authorized in the catalog"}`

**Causa:** Il `gameId` inviato nel request body non esiste nella tabella `game_catalog` del database locale.

**Soluzione:** Inserire il record nel catalogo:
```sql
INSERT INTO game_catalog (id, game_type, name, building_id, status)
VALUES ('game-foosball-1', 'FOOSBALL', 'Calciobalilla 1', 'building-1', 'AVAILABLE');
```

---

### 11.4 HTTP 429 su `/api/auth/login`

**Sintomo:** `{"error": "Too many failed login attempts. Please try again later."}`

**Causa:** Più di 5 tentativi di login falliti in 60 secondi per lo stesso username.

**Soluzione (sviluppo):**
```sql
DELETE FROM failed_login_attempts WHERE username = 'nomeutente';
```
Attendere 60 secondi oppure eliminare i record.

---

### 11.5 La tabella `outbox_events` cresce senza limite (POF-3)

**Sintomo noto:** La tabella `outbox_events` (locale e centrale) non ha TTL né job di cleanup.

**Stato attuale — RISOLTO lato Local Server, ANCORA APERTO lato Central System:**
- **Local Server:** implementato `OutboxPurgeService` (`@Scheduled`, elimina gli eventi `SENT` più vecchi di `app.outbox-purge-retention-days`, default 7 giorni) e `OutboxDlqPromotionService` (`@Scheduled`, promuove i `FAILED` in `outbox_dead_letter` e li rimuove da `outbox_events`). Le query manuali di workaround qui sotto non sono più necessarie lato Local.
- **Central System:** non esiste ancora un servizio di purge/DLQ equivalente; la tabella centrale `outbox_events` SENT continua a crescere senza limite. Residuo aperto.

**Workaround manuale (valido solo per il Central System finché non viene portato il purge):**
```sql
-- Eliminare gli eventi già inviati (SENT) più vecchi di 30 giorni
DELETE FROM outbox_events
WHERE status = 'SENT' AND created_at < DATE_SUB(NOW(), INTERVAL 30 DAY);
```

---

### 11.6 Race condition su GameSession/Reservation (POF-5)

**Sintomo noto:** In scenari di carico, è teoricamente possibile che due richieste concorrenti creino due sessioni attive sullo stesso gioco.

**Causa (storica):** Mancanza di `@Version` (ottimistic lock) su `GameJpaEntity` e `ReservationJpaEntity`.

**Stato attuale — IMPLEMENTATO (con residuo su `GameSessionJpaEntity`):**
- Aggiunta `@Version` (colonna `version BIGINT NOT NULL DEFAULT 0`) su `GameJpaEntity` e `ReservationJpaEntity`.
- `GameRepositoryAdapter` e `ReservationRepositoryAdapter` usano `saveAndFlush` e traducono `OptimisticLockingFailureException` → `com.gameplatform.local.domain.exception.ConcurrentStateException`.
- Lato REST: `GlobalExceptionHandler` mappa `ConcurrentStateException` → **409 Conflict**.
- Lato MQTT: `GameSessionListener` e `GameStateListener` catturano `ConcurrentStateException`, loggano e fanno ack (drop senza retry).
- Test: `BugL10`/`BugL11`/`BugL12`, guard test `GameRepositoryAdapterOptimisticLockGuardTest` / `ReservationRepositoryAdapterOptimisticLockGuardTest`, e2e `B17ConcurrentGameMachineStartOptimisticLockTest`.
- **Residuo accettato:** `GameSessionJpaEntity` è stata lasciata intenzionalmente senza `@Version`; un `end()` concorrente può ancora produrre un doppio `GAME_SESSION_COMPLETED`.

---

### 11.7 Sync starvation con outbox grandi (POF-7)

**Sintomo noto:** Se `outbox_events` locale ha migliaia di record PENDING, `findPending()` carica tutto in memoria.

**Stato attuale — IMPLEMENTATO:**
- `SyncSchedulerService` riscritto come ibrido Option-C: lettura limitata via `findPendingLimit(batchSize)` con `app.outbox.batch-size` (default 50) al posto dell'illimitato `findPending()`.
- Sul successo del trasporto del batch → `markAsSentBatch` atomico (preserva il contratto `BugL05`).
- Sul fallimento del trasporto → retry per-event con isolamento del poison: per-event `markAsSent(id)` / `incrementRetry(id)`, `try/catch` per singolo evento e `continue`. Un evento poison non blocca il resto del batch.
- Dopo 10 retry l'evento va in `FAILED` e viene promosso in `outbox_dead_letter` dal `OutboxDlqPromotionService`.
- Nuove port methods `findPendingLimit(int)` e `markAsFailed(String)`; indice composito `idx_outbox_status_created_at (status, created_at)` su `outbox_events`.
- Test: `BugL07_SyncStarvationPoisonIsolationTest`; `SyncSchedulerServiceTest` e `BugL05` aggiornati al nuovo contratto.

---

### 11.8 `InternalApiKeyFilter` blocca il Local Server in sviluppo

**Sintomo:** `403 Forbidden` sulle chiamate `/internal/**` anche con l'API key corretta.

**Causa:** La variabile d'ambiente `INTERNAL_API_KEY` non corrisponde tra il chiamante e il destinatario.

**Soluzione:**
```bash
# Verificare che i valori corrispondano nei due servizi
printenv INTERNAL_API_KEY
docker exec gamehandler-platform-central-system-1 printenv INTERNAL_API_KEY
```

---

### 11.9 JWT key temporanea genera token incompatibili al restart

**Sintomo:** Dopo il riavvio del microservizio, tutti i JWT precedenti diventano invalidi.

**Causa:** `JwtTokenProvider` genera una coppia RSA temporanea in memoria se il file PEM non viene trovato. Al riavvio, la chiave cambia.

**Soluzione:** Assicurarsi che i file PEM siano montati correttamente come volume Docker:

```yaml
# Nel docker-compose.yml (già configurato)
volumes:
  - ./infrastructure/tls:/certs:ro
```

---

## 12. Contribuzione

### 12.1 Branching Strategy

```
main              ← Branch stabile, sempre deployable
├── develop       ← Integrazione feature (branch principale di lavoro)
│   ├── feature/nome-feature    ← Una feature per branch
│   ├── bugfix/nome-bug         ← Fix di bug
│   └── hotfix/nome-hotfix      ← Fix critici da mergeare direttamente su main
```

**Convenzione nomi branch:**
- `feature/jwt-refresh-token`
- `bugfix/pof-5-optimistic-lock`
- `hotfix/outbox-overflow`

### 12.2 Commit Message

Seguire il formato **Conventional Commits**:

```
<tipo>(<scope>): <descrizione breve in italiano o inglese>

[corpo opzionale: perche, non cosa]
[riferimento issue: closes #42]
```

**Tipi:** `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `perf`

**Esempi:**
```
feat(local-server): aggiunge paginazione a findPending() in SyncSchedulerService
fix(central): corregge race condition su AggregatedStatistics con lock pessimistico
test(auth): aggiunge test per rate limiting per-username
docs: aggiorna DESIGN.md con ADR-006 su strategia persistenza JSON
```

### 12.3 Pull Request

1. **Branch:** Aprire la PR da `feature/*` verso `develop`.
2. **Descrizione:** Descrivere cosa cambia, perché, e come testarlo.
3. **Checklist PR:**
   - [ ] I test unitari passano (`mvn test`)
   - [ ] Non sono state aggiunte dipendenze nel `domain/` da framework (`org.springframework.*`, `jakarta.persistence.*`)
   - [ ] Le eccezioni di dominio sono tipizzate (non `RuntimeException` raw)
   - [ ] La pubblicazione MQTT è deferita post-commit
   - [ ] Il `DESIGN.md` è aggiornato se è cambiata l'architettura
4. **Review:** Almeno un altro membro del team deve approvare prima del merge.

### 12.4 Aggiungere un nuovo tipo di gioco

1. In `shared-domain`: aggiungere il valore a `GameType` enum.
2. In `shared-domain/result/`: creare `NuovoGiocoResult implements GameResult` (record) con annotazione `@JsonSubTypes.Type`.
3. In `game-client-emulator/domain/games/`: creare `NuovoGiocoGame implements GameLifecycle + [capabilities]`.
4. In `GameFactory.java`: aggiungere il case al factory method.
5. In `infrastructure/mysql-local/init.sql`: inserire i record del nuovo gioco nel `game_catalog`.
6. Aggiornare `DESIGN.md` se le capabilities del gioco introducono nuove interfacce.

> Vedere `[aggiunta_giochi_azzardo.md]` per un esempio concreto di questa procedura applicata a SLOT_MACHINE e ROULETTE.

### 12.5 Aggiungere un nuovo edificio

Non richiede modifiche al codice. Aggiungere in `docker-compose.yml`:

```yaml
# Nuovo DB locale
local-db-2:
  image: mysql:8.0
  environment:
    MYSQL_ROOT_PASSWORD: ${LOCAL_DB_PASSWORD:-root}
    MYSQL_DATABASE: local_db
  volumes:
    - ./infrastructure/mysql-local/init.sql:/docker-entrypoint-initdb.d/init.sql
    - local-db-2-data:/var/lib/mysql
  networks: [local-net-2]

# Nuovo broker MQTT (con nuovi certificati TLS)
mqtt-broker-2:
  image: eclipse-mosquitto:2.0
  # ... configurazione analoga a mqtt-broker-1
  networks: [local-net-2]

# Nuovo Local Server
local-server-2:
  build:
    context: ./local-server
  environment:
    - BUILDING_ID=building-2   # Unica differenza rispetto a local-server-1
    - SPRING_DATASOURCE_URL=jdbc:mysql://local-db-2:3306/local_db
    - MQTT_BROKER_URL=ssl://mqtt-broker-2:8883
    # ... altri parametri analoghi
  networks: [local-net-2, integration-net]

# Nuova rete
networks:
  local-net-2:

volumes:
  local-db-2-data:
```

---

## 13. FASE 7 — Utenti, Tornei e Dashboard multi-edificio

> La FASE 7 introduce il pattern outbox `*_REQUESTED` per le scritture admin/PLAYER async, le 5 nuove tabelle Local di replica, le viste PLAYER dai Local, le 3 dashboard admin nel Game Client Emulator, il flow `admin_requests_local` con timeout service, e i 2 test di contratto architetturale (`EventTypeContractTest` + `ReplicationEventTypeContractTest`).

### 13.1 Viste PLAYER (FASE 7 S5)

- `TournamentsView` (`infrastructure/ui/`): catalogo tornei dal Local (`GET /api/tournaments[?status=]`) basato sulla replica `tournaments_summary_local`; dettaglio torneo, classifica, match e partecipanti (read-only riuso `tournament_standings_local`/`tournament_participants_local`/`tournament_matches_local`).
- `MyMatchesView`: "I miei match" (`GET /api/players/tournaments/me/matches`) + "Avvia match" (`POST /api/players/tournaments/matches/{matchId}/start`). Limit nota: team-match membership richiede futura tabella `team_members_local` (vedi §19.7 limiti noti architettura_classi.md).
- `MyStatisticsView`: statistiche personali riuso `GET /api/players/me/statistics` (FASE 3).
- Iscrizione PLAYER async: `POST /api/tournaments/{id}/participants` → outbox `PARTICIPANT_REGISTER_REQUESTED` → polling `AdminRequestsView`. Latenza tipica ≤5 min (vedi limiti noti §7.D (a)).
- Servizio orchestration: `application/service/PlayerTournamentFlow` incapsula le 4 chiamate API.

### 13.2 Dashboard Admin (FASE 7 S5)

- `LocalAdminDashboard` (VIEW_LOCAL_ADMIN): sezioni giochi (`/api/admin/local/games`), dispositivi (`/api/admin/local/devices`), sessioni attive (`/api/admin/local/sessions/active`), statistiche edificio (`/api/admin/local/statistics`). Endpoint FASE 1 riusati; enforcement A3 via `local_admin_buildings_local`.
- `GameAdminDashboard` (VIEW_GAME_ADMIN): catalogo definizioni `GET /api/admin/games` locale + editor `POST/PUT /api/admin/games` → outbox `GAME_DEFINITION_UPSERT_REQUESTED` → `AdminRequestDto(PENDING)` → polling.
- `PlatformAdminDashboard` (VIEW_PLATFORM_ADMIN):
  - Gestione utenti e assegnazione ruoli (`GET /api/admin/users` + `POST /api/admin/users/{userId}/roles` → outbox `ROLE_ASSIGNMENT_REQUESTED`).
  - Binding LOCAL_ADMIN↔building (`POST/DELETE/GET /api/admin/local/buildings`).
  - Lifecycle tornei (`POST /api/admin/tournaments` + `POST /{id}/{open|cancel|schedule}` + `PUT/DELETE /{id}` DRAFT-only → outbox `TOURNAMENT_*_REQUESTED`).
  - Classifiche/bracket read-only (riuso viste PLAYER).
  - Statistiche globali (`GET /api/statistics` locale aggregato per building, oppure `GET /api/admin/local/statistics` esteso).
  - Monitoraggio local-server (`GET /api/admin/servers/health` → `ServerHealthViewDto`).
  - Super-set read-only delle dashboard LocalAdmin/GameAdmin (navbar visibile, bottoni di scrittura nascosti; `@PreAuthorize` lato server resta specifico).

### 13.3 `ApiClient` (FASE 7 S5)

- `infrastructure/rest/ApiClient` + `HttpClientHelper.setRoles/setBuildings`: bearer JWT per tutte le chiamate autenticate; `GET /api/auth/me` per bootstrap info utente (`userId`, `roles`, `buildings`).
- `NavbarController`: voci navbar condizionate al ruolo JWT (claim `roles`); super-set read-only per `PLATFORM_ADMIN`.
- Riferimenti ai nuovi DTO in `shared-dto`: `TournamentSummaryDto`, `TournamentDetailDto`, `TournamentParticipantViewDto`, `PlayerMatchDto`, `UsersDirectoryDto`, `ServerHealthViewDto`, `AdminRequestDto`, `RoleAssignmentRequestDto`, `UpdateTournamentRequestDto`, `RegisterTournamentParticipantDto`, `CreateTournamentRequestDto`, `UpsertGameDefinitionRequestDto`, `GameDefinitionDto`.
- Test manuali: `docker-compose up` (singolo + multi-building). Nessun test automatico UI (copertura manuale, piano §641).

### 13.4 `admin_requests_local` flow (FASE 7 S4)

- Tabella Local `admin_requests_local` persiste le richieste async W6/W9/W10/W12 (PIANO §7.B). Scritta atomicamente con la riga `OutboxEvent` (UUID `requestId == outbox eventId`) dall'`AdminRequestOutboxWriter` `@Component` nello stesso `@Transactional` caller.
- Lifecycle: `PENDING → COMPLETED` (chiamato dal `*SyncService` quando l'evento di ritorno Central reca `originatingRequestId != null`) OPPURE `PENDING → FAILED` (chiamato dall'`AdminRequestTimeoutService` a timeout).
- `AdminRequestTimeoutService` `@Service @Scheduled` (poll ogni `${admin.request-timeout-check-ms:60000}`): `findPendingOlderThan(now.minus(timeoutMs, MILLIS))` + `markFailed(requestId, "{\"reason\":\"TIMEOUT\"}", now)` per-riga idempotente (`WHERE status='PENDING'` + check del `int` ritornato dall'`@Modifying @Query`).
- Indici DB: `(acting_user_id, status)` per `AdminRequestsController.per-user`, `(status, created_at)` per la query timeout.
- DRAFT pre-check per W12e (update) e W12f (delete): se `tournaments_summary_local.status != DRAFT` (o summary missing) → `writeFailedRequest` con `result_data={"reason":"NOT_DRAFT"|"NOT_FOUND"}` (no outbox).
- Self-service admin requests: `AdminRequestsController` (Local) `GET /api/admin/requests[/{requestId}]` con filtro cross-user `actingUserId==principal`.

### 13.5 Test di contratto architetturale (FASE 7 S6)

- `EventTypeContractTest` (Central): pinna ogni literal Local-emitted (15 al totale, 8 nuovi FASE 7 §7.A.7/S3 + 6 W use case + PLAYER_PARTICIPANT) ad un branch esplicito in `SyncEventProcessor.processEvent`. Verificato bidirezionale: ogni literal è gestito Central-side ed emesso Local-side.
- `ReplicationEventTypeContractTest` (Central, nuovo S6 gap S1 §16.7 A5): pinna ogni literal Central-emitted drained da `UserReplicationSchedulerService.isReplicationEvent` (10: USER_REGISTERED, USER_UPDATED, LOCAL_ADMIN_BUILDING_ASSIGNED/REVOKED, GAME_DEFINITION_UPSERTED, TOURNAMENT_MATCH_SCHEDULED, TOURNAMENT_SUMMARY_UPSERTED, TOURNAMENT_STANDINGS_UPSERTED, TOURNAMENT_PARTICIPANTS_UPSERTED, LOCAL_SERVER_REGISTRY_UPSERTED) ad (a) una dichiarazione come literal in `UserReplicationSchedulerService.java` AND (b) un emission come literal in uno degli 8 producer Central (`UserService`/`LocalAdminBuildingService`/`GameDefinitionService`/`TournamentService`/`TournamentStandingsService`/`TournamentRegistrationService`/`TournamentMatchOutboxAdapter`/`LocalServerRepositoryAdapter`).

### 13.6 Stato moduli post-FASE 7

Central: 345 test ✅ (343 baseline + 2 `ReplicationEventTypeContractTest`). Local: 798 test ✅. Shared-domain/dto/mqtt: build verde. e2e-tests: 5 failure pre-esistenti (legacy role "USER"→"PLAYER", double replication_progress) — out-of-scope §7.D; follow-up FASE 8.

---

## 14. Integrazione futura con dispositivi IoT (tavoli fisici)

> Questa sezione analizza come il sistema, oggi basato sull'emulatore JavaFX, potrebbe in futuro integrare **veri tavoli di gioco IoT** (calciobalilla, biliardo, freccette, ecc.) sui quali i giocatori si siedono, fanno login tramite tessera NFC e possono giocare (anche all'interno di tornei a iscrizione).
>
> Non esiste ancora codice C/C++ embedded nel repository. I riferimenti a IoT/ESP32 nei documenti (`DESIGN.md:438`, `VISION.md:120,136,157`) sono solo dichiarativi: il firmware del tavolo è tutto da scrivere. Il presente documento descrive invece cosa è già riusabile e cosa manca nel backend e nel model per accogliere i dispositivi fisici.

### 14.1 Architettura attuale: Hub & Spoke

In questa sezione si descrive l'architettura a tre tier su cui poggia la piattaforma oggi, per chiarire quali componenti entrano in gioco quando si parla di lobby, login, building ed elenco giochi, e perché l'integrazione IoT si appoggia a queste stesse strutture.

- **Central System** (`:8180` HTTPS): utenti, tornei, statistiche globali, game definitions, registro Local Server. MySQL `central_db`. È la source of truth globale e replica gli utenti verso i Local Server tramite Transactional Outbox.
- **Local Server** (`:8181`/`8182`/`8183`, **uno per building**): sessioni/prenotazioni/lobby, MQTT gateway, offline-first su `replicated_users`. MySQL `local_db`. Si auto-registra al Central (`POST /internal/servers/register`).
- **Game Client Emulator** (JavaFX): parla con il Local Server via **REST** (login, tornei, elenco giochi, "my matches") e **MQTT over TLS** (lobby, play, heartbeat). Nessuna UI web.

La comunicazione cross-tier è di tipo Hub-and-Spoke: Local↔Central via REST + Transactional Outbox (consistenza eventuale, scheduler a 5 min); Client↔Local via MQTT Pub/Sub con mTLS con CN del certificato = `gameId`. I topic MQTT seguono lo schema `building/{buildingId}/game/{gameId}/{action}` (`shared-mqtt/.../MqttTopics.java:6-66`).

Questo implica che il trasporto è già disegnato in modo "device-agnostic": un tavolo fisico, per integrarsi, dovrebbe semplicemente produrre un CSR, connettersi via mTLS e pubblicare/sottoscrivere gli stessi topic del client emulator.

### 14.2 Login multi-giocatore e identità del client

In questa sezione si chiarisce come funziona oggi il login del giocatore, se un'istanza dell'emulatore può ospitare più identificativi contemporaneamente e come viene risolta l'identità lato server. È il punto centrale per capire la differenza con il tavolo fisico multi-seat.

**Il login è per-client, un solo giocatore per istanza emulator.** Un client/emulatore **non** fa login di più giocatori contemporaneamente nello stesso building.

- `LoginView.performLogin()` (`game-client-emulator/.../ui/LoginView.java:89-138`): un singolo form `username/password`; `POST /api/auth/login` → poi `GET /api/auth/me` per arricchire `UserInfoDto` (userId, roles, buildings) immesso in `HttpClientHelper` (`:108-118`). La navbar e tutte le view sono guidate da quell'unica identità (`MainView.java:183-204`).
- **Endpoint login lato Local**: `AuthController.login` (`local-server/.../adapters/in/rest/AuthController.java:47-51`) → `AuthenticateLocalUserUseCase.authenticate` → `LocalAuthService.authenticate` (`local-server/.../application/service/LocalAuthService.java:30-46`). **Autentica localmente contro `replicated_users`** (offline-first): `BCrypt.checkpw(password, user.getPasswordHash())` + emissione JWT locale RS256. **Non delega al Central** a runtime: gli utenti arrivano al Local per replica Central→Local via outbox (`USER_REGISTERED`/`USER_UPDATED`) con cadenza ~5 min.
- **Identità server-facing**: i participant inviati via MQTT (username o UUID) vengono "canonicalizzati" a UUID dal `GameSessionService.resolveCanonicalUserId` (`GameSessionService.java:156-167`) prima di scriverli sulla `GameSession`/outbox, così le statistiche del giocatore sul Central (`player_statistics`) chiaveano sull'UUID letto dal JWT.

**Implicazione operativa**: per giocare in multi-giocatore con l'emulatore occorre avviare **più istanze emulator separate**, ciascuna con il proprio login e il proprio `GAME_ID`, che join-ano la stessa lobby sullo stesso `GameId` (vedi §14.5). Il `docker-compose.yml` provvede infatti due container `game-client-1`/`game-client-2`.

### 14.3 Istanze client e concetto di building

In questa sezione si spiega che cosa succede quando si avvia un nuovo client: il building non viene creato dal client ma è staticamente pre-configurato, e il client si collega a un building esistente puntando al Local Server corretto. Risponde al dubbio "ogni client che avvio crea building diversi?".

**Il building non viene creato dal client: è staticamente pre-configurato.**

- Lato Local Server: `app.building-id` in `local-server/src/main/resources/application.yml` (es. `building-1` — `README.md:326`).
- Lato client: env `BUILDING_ID` (default `building-1`, `MainView.java:65,109`); l'URL del Local Server viene da env `LOCAL_SERVER_URL` (default `https://localhost:8181`, `ApiClient.java:49`).
- I building validi sono predefiniti (`building-1/2/3`); `VISION.md:183` dichiara esplicitamente: «i `building_id` siano pre-configurati staticamente (non esiste una UI di provisioning per nuovi edifici)».
- `ApiClient.BUILDING_URLS` (`ApiClient.java:58-62`) mappa `building-1/2/3 → :8181/8182/8183` per il selettore building del `PLATFORM_ADMIN` (`ApiClient.setBaseUrl` `:97-102`).

Quindi **ogni client si collega a un building esistente**. Per avere due giocatori in `building-1` si lanciano due emulatori con `BUILDING_ID=building-1` (e `GAME_ID` diversi, perché il CN del certificato mTLS coincide con il `gameId` e identifica il "dispositivo" rappresentato da quell'istanza). I building aggiuntivi si abilitano con il multi-compose: `docker-compose.multi.yml` definisce gli override per `building-2` (porte `8182/8884/3308`) e `building-3` (`8183/8885/3309`).

### 14.4 Catalogo giochi e lista in GUI emulator

In questa sezione si descrive come è modellato il fatto che "ogni building ha più giochi dello stesso tipo e molti giochi in genere", e come in GUI emulator i giochi appaiono effettivamente come lista selezionabile dopo il login.

**Ogni building = un Local Server + un DB MySQL locale + un broker Mosquitto + N game machines** (`DESIGN.md:267-292`, `README.md:295-301`). Il building è identificato da `BuildingId` (record in `shared-domain/model`); le `Game` hanno un `buildingId` (`Game.getBuildingId()` usato in `GameController.toDto` `:74` e nei topic MQTT `MqttTopics.gameState(buildingId, gameId)`).

**Enumerazione/lista dei giochi di un building** = tabella `game_catalog` (DB locale), una riga per **macchina da gioco**:

```
game_catalog (id, game_type, name, building_id, status)  -- init.sql:125-129
```

Seed `building-1` (`infrastructure/mysql-local/init.sql:125-129`): 4 righe — `game-chess-1/CHESS`, `game-foosball-1/FOOSBALL`, `game-darts-1/DARTS`, `game-slot-1/SLOT_MACHINE`, tutte `AVAILABLE`. I building 2 e 3 hanno seed propri (`init-building-2.sql:125`, `init-building-3.sql:125`).

**Un building può avere più giochi dello stesso tipo e di tipi diversi**: non esiste un vincolo di unicità `(building, gameType)`. Il `LOCAL_ADMIN` può aggiungere altri game machine via `POST /api/admin/local/games` → `GameCatalogService.createGame(gameType, name, buildingId)` (`local-server/.../application/service/GameCatalogService.java:33-47`), generando un nuovo `GameId` random e una riga `game_catalog` con `status=AVAILABLE`. Più tavoli foosball nello stesso building sono dunque multipli `GameId` distinti dello stesso `GameType`.

**In GUI emulator i giochi appaiono come lista**: sì. `GameSelectionView` mostra una `ListView<GameStateDto>` popolata da `GET /api/games` (`GameSelectionView.java:56-79`, `:160-172`); ogni cella renderizza `{name} [{gameType}] - {status}` con suffisso `(open lobby)` quando `LOBBY` (`:66-67`). Aggiornamenti di stato real-time via MQTT `building/{bid}/game/+/state` (`:124-142`). Le definizioni `minPlayers`/`maxPlayers` provengono da `game_definitions_local` (replicata dal Central, `GameController.toDto` `:58-79`).

Il `GameType` è un enum condiviso in `shared-domain`: `FOOSBALL, CHESS, DARTS, MONOPOLY, POKER, CONNECT4, BATTLESHIP, SLOT_MACHINE, ROULETTE, RISK` (`README.md:155`). Le `GameDefinition` (min/max, teamAllowed, registrationRules) vivono sul Central `game_definitions` e sono replicate ai Local come `game_definitions_local`.

### 14.5 Evoluzione del concetto di lobby con tavoli IoT fisici

In questa sezione si approfondisce come è modellata oggi la lobby lato server e cosa cambia nel concetto di lobby quando si sostituisce l'emulatore GUI con un tavolo IoT fisico: il tavolo fisico diventa "il tavolo/game machine" stesso, il login NFC sostituisce il form, e il multi-giocatore diventa multi-seat sullo stesso dispositivo. Si evidenziano i flussi server-side già riusabili e quelli da adattare.

#### 14.5.1 Modello di lobby attuale

La lobby è uno **stato server-side** definito dall'enum `GameMachineStatus.LOBBY` (`shared-domain/.../model/GameMachineStatus.java:8`) unito a una `GameSession` con `GameStatus.WAITING`. Il flusso completo:

1. **Selezione gioco**: il player apre "Games" nel client → `GameSelectionView.refreshGames()` chiama `GET /api/games`. Solo giochi `AVAILABLE` (per creare) o `LOBBY` (per join) sono selezionabili (`GameSelectionView.java:115-121`).
2. **Creazione lobby (CREATOR)**: il client pubblica su MQTT `building/{bid}/game/{gid}/session/lobby/create` con `LobbyCreatePayload(gameType, creatorId)` (`game-client-emulator/.../mqtt/SessionPublisher.java:127-137`), ricevuto da `GameSessionListener.handleSessionMessage` (`local-server/.../adapters/in/mqtt/GameSessionListener.java:72-79`) che invoca `createLobbyUseCase.createLobby(...)`.
3. **`GameSessionService.createLobby`** (`GameSessionService.java:637-697`): verifica nessuna sessione attiva sul game (`:639-642`); carica il `Game` con lock pessimistico (`findByIdForUpdate`), se in stato `LOBBY` staccato lo rilascia (`:654-656`); **imposta `game.setLobby()`** → stato macchina `LOBBY` (`:659-660`); crea una `GameSession` con `status=WAITING` e partecipante iniziale = `[creatorId]` (`:666-679`); pubblica stato macchina + evento lobby/create via MQTT after-commit (`:684-694`).
4. **Join (JOINER)**: il secondo client vede il gioco come `LOBBY`, preme "Join" → pubblica `session/lobby/join` con `LobbyJoinPayload(sessionId, userId)` (`SessionPublisher.java:139-149`) → `GameSessionListener.java:80-83` → `joinLobbyUseCase.joinLobby(...)` → `GameSessionService.joinLobby` (`GameSessionService.java:700-732`): aggiunge il participant se `participants.size() < maxPlayers`, pubblica `session/lobby/join`. Il JOINER recupera `sessionId` via REST `GET /api/sessions/lobby/active?gameId=...` (`LobbyView.java:265-283`).
5. **Avvio partita (START)**: solo il creator può premere "▶ Start Match" (`LobbyView.java:124-127`): pubblica `session/lobby/start` con `LobbyStartPayload(sessionId)` (`SessionPublisher.java:151-159`) → `GameSessionListener.java:84-87` → `startLobbyUseCase.startLobby(...)` → `GameSessionService.startLobby` (`GameSessionService.java:735-770`): verifica `participants.size() >= min`, `game.startUse()` (macchina → `IN_USE`), session `WAITING → IN_PROGRESS`, pubblica stato + `session/lobby/start`. Il client poi naviga a `GamePlayView.setFromLobby(...)` (`MainView.java:216-219`).
6. **Cancel**: `session/lobby/cancel` → `cancelLobby` (`GameSessionService.java:773-809`): solo il creator può cancellare; rilascia la macchina a `AVAILABLE`.
7. **Scadenza automatica**: `LobbyExpirationService.expireLobbies()` ogni 60 s, lobby `WAITING` da >2 min abortite e macchina rilasciata (`local-server/.../application/service/LobbyExpirationService.java:44-90`).

Entità/classi coinvolte: `GameSessionService` (implementa `CreateLobbyUseCase`, `JoinLobbyUseCase`, `StartLobbyUseCase`, `CancelLobbyUseCase`, `GetActiveLobbyUseCase` — `GameSessionService.java:61`), `GameSessionListener` (MQTT in), `GameSession` (domain model), `Game.setLobby()/startUse()/release()`, payload `LobbyCreatePayload`/`JoinPayload`/`StartPayload`/`CancelPayload` (`shared-mqtt/.../payload/`), `LobbyView` + `SessionPublisher` + `StateSubscriber` lato client. Test e2e: `A5ClientLobbyCreateJoinStartMultiplayerSessionTest`.

> La lobby è **associata a un singolo `GameId`** (una macchina/gioco): il "tavolo" è già il game machine. Multi-giocatore = multi-partecipanti alla stessa `GameSession`.

#### 14.5.2 Cosa cambia con il tavolo IoT fisico

Lo stato server-side può restare in gran parte invariato; cambia il modo in cui i giocatori entrano nella lobby e identificano se stessi.

**14.5.2.1 — Lobby = il tavolo fisico stesso.** Nel modello attuale un `GameId` è già "il tavolo" (`Game.status` ha `LOBBY`; un solo tavolo/`GameId` può essere in lobby per volta). Con dispositivi IoT il tavolo fisico è la materializzazione di quel `GameId`; la lobby `WAITING` diventa "il tavolo è acceso, in attesa di badge-in". Lo `GameSessionService.createLobby` (`:637-697`) invece che sul "creator client" dovrebbe poter essere **avviato dal tavolo stesso** quando un giocatore si siede e fa badge-in, generando la `GameSession` con `participants=[userId]`. Il tavolo quindi assume il ruolo che oggi ha il "CREATOR" client.

**14.5.2.2 — Il login NFC sostituisce il login GUI.** Nel flow GUI, il `username/password` → JWT RS256 (`LocalAuthService.java:30-46`) è eseguito sull'istanza emulator, e quel JWT/cert identifica la sessione di quello user su quel client MQTT. Sostituendo il tavolo fisico, il tavolo non ha un "login form": ogni giocatore si autentica appoggiando un badge NFC a un lettore del tavolo. Lato sistema, serve:

- una nuova tabella `player_cards(card_uid → user_id)` replicata Central→Local (come `replicated_users`);
- il tavolo, letto l'UID, lo invia al Local Server (REST `POST /api/sessions/lobby/{sessionId}/seat` con `cardUid`, oppure pubblica MQTT `session/lobby/join` con `userId` risolto);
- il Local Server risolve `card_uid → userId` e chiama `GameSessionService.joinLobby(sessionId, userId)` (`:700-732`) già esistente.

Quindi **`joinLobby` non richiede modifiche strutturali**: basta cambiare l'origine del `userId` (NFC invece che form) e il tavolo publica per conto del giocatore seduto. Va però gestito il **trust**: il tavolo deve firmare la propria origine del messaggio (cert del tavolo = già mTLS con CN=deviceId); il `userId` risolto dall'NFC lato Local, non trust-ato dal client. Il design attuale (creatorId/userId inviati dal client e canonicalizzati server-side, `GameSessionService.resolveCanonicalUserId` `:156-167`) **trusta il client** — va invertito: il tavolo invia un `cardUid` grezzo, il Local risolve e authoritativezza.

**14.5.2.3 — Multi-seat (più giocatori fanno login allo stesso tavolo fisico).** Nel flow GUI multi-giocatore = "più emulatori, ognuno un login, join alla stessa lobby" (§14.2, §14.5). Nel tavolo fisico multi-seat è **un solo dispositivo con N lettori** (o un lettore condiviso e N badge-in sequenziali). Il model `GameSession.participants: List<UserId>` (`GameSessionService.java:678`) **già supporta N partecipanti** sullo stesso `GameId` — quindi serve solo che il tavolo pubblichi N `joinLobby` (uno per ogni badge) verso la stessa `sessionId`. Differenza operativa: nel GUI ogni client vede la sua schermata; nel tavolo fisico il tavolo **renderizza** lo stato pubblico (un display centrale) — non c'è una `GamePlayView` per giocatore. Il pattern GUI "io muovo dal mio client" è sostituito da "io faccio una mossa fisica e i sensori del tavolo generano l'evento `session/move`/`session/score`/`session/turn`" (`MqttTopics.java:32-54`) — i topic sono già pronti, ma il publisher è il **tavolo** invece del singolo client.

**14.5.2.4 — La lobby smette di essere "creata dall'esterno" e può essere "auto-aperta" dal tavolo.** Nel flow GUI il creator deve premere "Create Lobby" esplicitamente (`LobbyView.java:120-127`). Con un tavolo fisico "always-on" è naturale che **il tavolo apre autonomamente una lobby** quando torna `AVAILABLE` (o quando arriva il primo badge), invocando l'equivalente di `createLobby` direttamente dai suoi sensori — o, meglio, il Local Server lo fa `setLobby()` in autonomia al primo badge-in. `GameSessionService.createLobby` (`:659`) è riusabile; va aggiunto un path MQTT "tavolo→server" invece di "client→server".

**14.5.2.5 — Identità del giocatore vs "istanza client".** Con GUI: 1 istanza = 1 login = 1 `HttpClientHelper` statico (`LoginView.java:108-118`). Con tavolo fisico questa entità "sessione utente legata al processo client" **sparisce**: il tavolo è un processo "neutro" (autenticato come deviceId via mTLS), e ogni giocatore è un concetto **transiente** (badge-in → partecipa → badge-out/leave). Serve quindi:

- un endpoint/concept "player check-in/check-out al tavolo" che oggi non esiste (c'è solo `joinLobby`/`cancelLobby`);
- il `leaveLobby` non esiste neanche col GUI (solo `cancelLobby` che butta tutta la lobby — `GameSessionService.cancelLobby` `:773-809`) — con tavoli fisici serve un `leaveLobby(sessionId, userId)` che rimuova solo quel partecipante (per uscita pulita del giocatore badged-out senza distruggere la lobby per gli altri seduti).

**14.5.2.6 — Disaccoppiamento "client-logic" e "client-transport".** Oggi la logica di gioco (es. `ChessGame`, `FoosballGame` in `client/domain/games/`, `GameFactory`) è nell'emulatore JavaFX; gli eventi fisici sono gestiti dai relativi `*Panel` (es. `FoosballPanel` per i goal). Con un tavolo fisico, **la logica "scoring" deve spostarsi o sul firmware o sul Local Server** (il tavolo non deve conoscere "regole del gioco", solo eventi fisici: pulsante/lettore/sensore → evento; il Local già fa min/max validation via `GameSessionService.start` `:308-320` ma non interpreta mosse). Si può considerare di promuovere parti del `GameOrchestrationService`/pannelli a un servizio del Local o a un "controller del tavolo" (MCU secondario) — non presente oggi.

### 14.6 Tornei su tavoli fisici

In questa sezione si chiarisce come sono modellati oggi i tornei (a iscrizione self-service) e cosa serve per farli disputare su tavoli fisici prenotati. Si esclude volutamente la tematica degli inviti a torneo, fuori scope per questa trattazione.

I tornei sono pienamente implementati (FASE 4-7 del `PIANO_UTENTI_TORNEI.md`). Il modello di dominio (`PIANO_UTENTI_TORNEI.md:299-322`):

- `Tournament(id, name, gameType, teamBased, teamSize, format, status, startsAt, endsAt, createdBy)` con `TournamentStatus {DRAFT, OPEN_REGISTRATION, IN_PROGRESS, COMPLETED, CANCELLED}`.
- Un torneo **coinvolge un insieme di edifici** → tabella `tournament_buildings(tournament_id, building_id)` (`PIANO_UTENTI_TORNEI.md:325, :349-354`) e **un solo `gameType`**.
- `tournament_teams` + `tournament_team_members` (per tornei `teamBased`); `tournament_participants` (singoli o squadre); `tournament_matches(round, bracketPosition, participantA, participantB, buildingId, gameId, winner, status)`; `tournament_standings`.

Servizi Central: `TournamentService` (CRUD + lifecycle), `TournamentRegistrationService`, `TournamentBracketService` (single-elimination con byes), `TournamentStandingsService`. Controller `TournamentController` (`/api/tournaments`) + `TournamentRegistrationController`.

**Iscrizioni**: self-service, basate su "open registration" — `TournamentRegistrationService.register` (`central-system/.../application/service/TournamentRegistrationService.java:107-128`): valida `status==OPEN_REGISTRATION`, poi `registerIndividual` (`:130-144`) o `registerTeam` (`:146-173`). Endpoint `POST /api/tournaments/{id}/participants`.

**Lifecycle torneo** (`README.md:162-172`): `PLATFORM_ADMIN` crea → `open registration` → `PLAYER` si iscrivono → `schedule` (bracket) → `PLAYER` avvia i propri match via `POST /api/players/tournaments/matches/{matchId}/start` → `GameSessionService.start(..., tournamentMatchId)` (`GameSessionService.java:213-268`) lega la sessione al `TournamentMatchLocal`, flip a `IN_PROGRESS` → `end` emette outbox `GAME_SESSION_COMPLETED` + `TOURNAMENT_MATCH_COMPLETED` (`GameSessionService.java:531-566`) → Central `advanceWinner` → `COMPLETED` + `standings`.

Tornei su Local: `tournament_matches_local`, `tournament_participants_local`, `team_members_local`, `tournaments_summary_local`, `tournament_standings_local` (repliche read-only). Sync via `InternalTournamentController`/`InternalTournamentSummaryController`.

**Cosa manca per i tornei su tavoli fisici.** Oggi il `TournamentMatchLocal` ha `buildingId`/`gameId` (`PIANO_UTENTI_TORNEI.md:416-427`) ma **non c'è "reservation del tavolo per il match"**. Per i tornei su tavoli fisici serve:

- estendere `TournamentMatch` o `Reservation` con `tournament_match_id` (oggi `Reservation.java` non ha quel campo);
- uno stato aggiuntivo su `Game` "RESERVED_FOR_TOURNAMENT" o riusare `RESERVED` esistente (`GameMachineStatus.RESERVED`) legandolo al match;
- la lobby del tavolo fisico per un match di torneo deve accettare solo i 2 partecipanti del match (`GameSessionService.start` con `tournamentMatchId` già valida `participantA/B`, `:244-263`) — già pronto; manca solo la **prenotazione** del tavolo e il blocco di join esterni durante l'orario del match;
- il "no-show/forfait fisico" è gestibile con l'esistente `SessionAbortHelper.abortAndEmit` (FASE 6) che calcola walkover winner = partecipante assente.

### 14.7 Componenti mancanti per l'integrazione IoT reale

In questa sezione si elenca, area per area, cosa esiste oggi nel codice (con riferimento a file/classi) e cosa invece manca per realizzare l'integrazione con veri dispositivi IoT (tavoli fisici). È il sommario operativo di quanto discusso nei paragrafi precedenti.

| Area | Cosa c'è oggi | Cosa manca |
|---|---|---|
| **Firmware C++ tavolo** | niente (solo riferimenti dichiarativi in `DESIGN.md:438`, `VISION.md:120,136,157`) | Tutto: sensori, display, MQTT-TLS embedded (Paho-c), heartbeat, CSR enrollment su MCU |
| **Auth NFC** | `username/password` su `replicated_users` (`LocalAuthService.java:30-46`) | Tabella `player_cards(card_uid→user_id)` replicata; endpoint "check-in al tavolo"; risoluzione lato Local |
| **Trust model** | client invia `userId` trustato (`resolveCanonicalUserId:156-167`) | Capovolgerlo: tavolo invia `cardUid`, Local risolve authoritative |
| **Registry dispositivi** | `game_catalog` coincide col device (`init.sql:125-129`) | Tabella separata `physical_devices(device_id, game_id, building_id, hw_id, fw_version, last_seen)` + provisioning/associazione LOCAL_ADMIN |
| **ACL Mosquitto** | assente (`README.md:471`) | ACL pattern-based `building/{buildingId}/#` legata al CN del cert (oggi un device di un altro building potrebbe pubblicare ovunque) |
| **Multi-seat / leaveLobby** | `joinLobby`/`cancelLobby` (quest'ultima butta tutto, `GameSessionService.java:773-809`) | `leaveLobby(sessionId, userId)` singolo; model "seat" lato tavolo |
| **Prenotazione tavolo torneo** | `TournamentMatchLocal.{buildingId,gameId}` (`PIANO_UTENTI_TORNEI.md:416-427`) | `Reservation.tournament_match_id` + stato `RESERVED` sul `Game` durante il match + blocco join |
| **Discovery/auto-registrazione** | game machine creato a mano dal LOCAL_ADMIN (`GameCatalogService.createGame:33-47`) | Auto-register del tavolo al Local (`POST /internal/devices/register`) |
| **Heartbeat in lobby** | `HealthCheckService` abortisce solo `IN_PROGRESS` (`DESIGN.md:149-175`) | Abortire lobby `WAITING` se tavolo non heartbeatta |
| **Telemetria sensori** | topic `session/move/score/turn` generici (`MqttTopics.java:32-54`) | Standardizzare payload eventi fisici (goal, coordinate freccetta, ecc.) |
| **Logica di gioco** | nei `*Panel`/`GameFactory` dell'emulatore JavaFX | Spostarla o sul firmware o sul Local (il tavolo "sente" solo eventi grezzi) |
| **Rotazione cert / provisioning truststore** | bootstrap trust-all accettato in prototipo (`VISION.md:170`) | Rotazione auto (ADR-004 trade-off `:982`); provisioning truststore in fabbrica |
| **PKI/mTLS device** | già pronto via CSR (`CertificateEnrollmentService.java:57-160`, `DeviceRegistrationController` firma CSR con CN=gameId) | Riapplicabile; serve ACL Mosquitto per building-scoping (oggi assente) e provisioning truststore in fabbrica |
| **Comunicazione cross-tier** | MQTT over TLS + topic `building/{bid}/game/{gid}/...` (`MqttTopics.java:6-66`) + outbox sync | OK strutturalmente; eventuali topic aliases MQTT 5; WebSocket listener su Mosquitto per client web/mobile (`README.md:301`: assente) |

**Conclusione architetturale**: il sistema è già disegnato in modo "device-agnostic" al livello trasporto (MQTT + mTLS + topic `building/{bid}/game/{gid}/...` + outbox sync). L'integrazione con veri tavoli IoT **non richiede di rifare il domain model della lobby o della sessione**, ma richiede di: (1) scrivere il firmware C/C++ mancante; (2) introdurre autenticazione NFC con risoluzione `card_uid→userId` lato server; (3) capovolgere il trust model (il tavolo dice "badge X appoggiato" e non "io sono user Y"); (4) aggiungere un registry dispositivi separato dal game catalog, ACL MQTT per building, e prenotazione tavoli per match di torneo. I concetti di lobby, building, game machine e tournament match sono già sufficientemente astratti da ospitare i dispositivi fisici come ulteriori "client emulator" con un'origine di identità diversa.

---

*Fine documento implementazione_reale.md*
*Vedere [DESIGN.md](../DESIGN.md) per l'architettura del sistema.*
*Vedere [REQUIREMENTS.md](../REQUIREMENTS.md) per i requisiti verificabili.*
*Vedere [VISION.md](../VISION.md) per il contesto strategico del progetto.*
