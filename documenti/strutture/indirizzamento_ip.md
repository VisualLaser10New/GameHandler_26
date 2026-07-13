# Indirizzamento IP

Gli indirizzi IP utilizzati per comunicare tra i servizi sono definiti nella configurazione di ogni servizio.

### 1. Central System
Le configurazioni si trovano principalmente nel file [central-system/application.yml](file:///c:/Users/VLT14/Documents/UNI/PISSIR/Progetto/gamehandler-platform/central-system/src/main/resources/application.yml):
*   **Porta del server (SSL abilitato):** `8180` (definito alla riga 14 come `server.port: ${PORT:8180}`).
*   **Connessione al database Central:** `jdbc:mysql://localhost:3306/central_db` (riga 5). Può essere sovrascritto tramite la variabile d'ambiente `SPRING_DATASOURCE_URL`.
*   **Origini CORS consentite (Frontend/Console):** `http://localhost:3000` (riga 70). Può essere sovrascritto tramite `CORS_ALLOWED_ORIGINS`.

---

### 2. Local Server
Le configurazioni di default si trovano nel file [local-server/application.yml](file:///c:/Users/VLT14/Documents/UNI/PISSIR/Progetto/gamehandler-platform/local-server/src/main/resources/application.yml):
*   **Porta del server (SSL abilitato):** `8181` (definito alla riga 16 come `server.port: ${PORT:8181}`).
*   **URL del Central System:** `https://localhost:8180` (riga 27 sotto `app.central-system-url`). Sovrascritto dalla variabile `CENTRAL_SYSTEM_URL`.
*   **URL del Broker MQTT locale:** `tcp://localhost:1883` (riga 43 sotto `mqtt.broker-url`). Sovrascritto da `MQTT_BROKER_URL`.
*   **Base URL del Local Server stesso (comunicato al Central per callback):** `https://local-server-1:8181` (riga 26 sotto `app.local-base-url`). Sovrascritto da `LOCAL_BASE_URL`.
*   **Connessione al database locale:** `jdbc:mysql://localhost:3307/local_db` (riga 7). Sovrascritto da `SPRING_DATASOURCE_URL`.

---

### 3. Game Client Emulator
Le configurazioni di default si trovano nel file [game-client-emulator/application.yml](file:///c:/Users/VLT14/Documents/UNI/PISSIR/Progetto/gamehandler-platform/game-client-emulator/src/main/resources/application.yml):
*   **URL del Local Server:** `https://localhost:8181` (riga 9 sotto `app.local-server-url`). Sovrascritto dalla variabile `LOCAL_SERVER_URL`.
*   **URL del Broker MQTT:** `tcp://localhost:1883` (riga 15 sotto `mqtt.broker-url`). Sovrascritto da `MQTT_BROKER_URL`.

*(Nota: Nel codice dell'emulatore, ad esempio in [ApiClient.java](file:///c:/Users/VLT14/Documents/UNI/PISSIR/Progetto/gamehandler-platform/game-client-emulator/src/main/java/com/gameplatform/client/infrastructure/rest/ApiClient.java#L49), viene utilizzato come fallback `https://localhost:8181` (costante `DEFAULT_BASE_URL`) qualora la variabile `LOCAL_SERVER_URL` non sia definita).*

---

### 4. Configurazione Docker Compose (Ambiente Integrato)
Quando i servizi vengono eseguiti tramite Docker Compose, i container comunicano all'interno di reti virtuali Docker utilizzando i **nomi dei servizi** come hostname al posto di `localhost`.

Questi dettagli sono definiti in:
*   [docker-compose.yml](file:///c:/Users/VLT14/Documents/UNI/PISSIR/Progetto/gamehandler-platform/docker-compose.yml):
    *   **Central System -> Database:** `jdbc:mysql://central-db:3306/central_db` (riga 41).
    *   **Local Server 1 -> Database:** `jdbc:mysql://local-db-1:3306/local_db` (riga 108).
    *   **Local Server 1 -> Broker MQTT:** `ssl://mqtt-broker-1:8883` (riga 109).
    *   **Local Server 1 -> Central System:** `https://central-system:8180` (riga 112).
    *   **Game Client 1 & 2 -> Local Server 1:** `https://local-server-1:8181` (righe 150, 168).
    *   **Game Client 1 & 2 -> Broker MQTT:** `ssl://mqtt-broker-1:8883` (righe 149, 167).
*   [docker-compose.multi.yml](file:///c:/Users/VLT14/Documents/UNI/PISSIR/Progetto/gamehandler-platform/docker-compose.multi.yml) (per scenari multi-edificio):
    *   Configura in modo analogo le connessioni per l'edificio 2 e l'edificio 3 utilizzando rispettivamente gli host `local-db-2`, `mqtt-broker-2`, `local-server-2` e `local-db-3`, `mqtt-broker-3`, `local-server-3`.