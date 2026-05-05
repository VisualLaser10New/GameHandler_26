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
1. Cercate nel progetto le classi `main` dei tre microservizi (es. `CentralSystemApplication.java`, `LocalServerApplication.java`).
2. Cliccate sulla freccia verde **Play** (Run) a fianco della classe in IntelliJ.
3. Le applicazioni Spring Boot partiranno nativamente sul vostro PC, leggeranno i file `application.yml` che puntano a `localhost:3306` (dove Docker sta inoltrando il DB) e si connetteranno correttamente.
Questo vi permette di usare il Debugger di IntelliJ in modo fulmineo, senza riavviare Docker a ogni singola riga di codice modificata!

---

## 4. Ambiente di Produzione (Per l'Esame e la Consegna)

Mentre l'Approccio Ibrido è perfetto per lo *sviluppo*, il professore dovrà poter eseguire il progetto completo in un solo click, senza dover aprire IntelliJ o configurare Java. 

Per l'ambiente di produzione (o "Test Finale" per voi prima della consegna), si sfrutta **interamente** la potenza di Docker. Il file `docker-compose.yml` è configurato in modo che i `Dockerfile` prelevino il codice sorgente, lo compilino all'interno del container isolato e avviino i server.

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
