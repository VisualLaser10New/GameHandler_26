# IMPLEMENTATION.md — GameHandler_26: Boardgame Platform

> **Versione:** 1.0 | **Data:** 2026-06-29 | **Corso:** PISSIR, 3° anno
>
> Guida pratica per sviluppatori, DevOps e contributor. Copre setup locale, build, test, debugging, deployment e contribuzione al progetto.

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

Il Local Server implementa un `HealthCheckService` che verifica periodicamente i client tramite heartbeat MQTT. Non è implementato un endpoint `/actuator/health` standard di Spring Boot Actuator.

[DA CHIARIRE: non sono presenti dipendenze `spring-boot-starter-actuator` nei `pom.xml`. Aggiungere Actuator permetterebbe di esporre `/actuator/health` per monitoring esterno.]

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

**Workaround attuale:**
```sql
-- Eliminare gli eventi già inviati (SENT) più vecchi di 30 giorni
DELETE FROM outbox_events
WHERE status = 'SENT' AND created_at < DATE_SUB(NOW(), INTERVAL 30 DAY);
```

**Fix proposto (non implementato):** Aggiungere un `@Scheduled` che esegue periodicamente il cleanup.

---

### 11.6 Race condition su GameSession/Reservation (POF-5)

**Sintomo noto:** In scenari di carico, è teoricamente possibile che due richieste concorrenti creino due sessioni attive sullo stesso gioco.

**Causa:** Mancanza di `@Version` (ottimistic lock) su `GameJpaEntity` e `ReservationJpaEntity`.

**Fix proposto (non implementato):** Aggiungere `@Version private Long version;` alle entità JPA coinvolte.

---

### 11.7 Sync starvation con outbox grandi (POF-7)

**Sintomo noto:** Se `outbox_events` locale ha migliaia di record PENDING, `findPending()` carica tutto in memoria.

**Fix proposto (non implementato):** Aggiungere paginazione (`LIMIT 100`) e una Dead Letter Queue (DLQ) per eventi con `retry_count` elevato.

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
main              ← Branch stabile, sempre deployabile
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

*Fine documento IMPLEMENTATION.md*
*Vedere [DESIGN.md](DESIGN.md) per l'architettura del sistema.*
*Vedere [REQUIREMENTS.md](REQUIREMENTS.md) per i requisiti verificabili.*
*Vedere [VISION.md](VISION.md) per il contesto strategico del progetto.*
