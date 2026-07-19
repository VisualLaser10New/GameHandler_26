---
title: Relazione - Game Handler
subtitle: Progetto del corso di PISSIR - 2025/26
category: Progetto del corso di PISSIR
author: Emanuele Trento, Davide Castellani, Tiziano Ceccon
date: 07/19/2026
---

# GAME HANDLER

Progetto del corso di PISSIR, anno 2025/26

## 1. DESCRIZIONE DEL PROGETTO
### Obbiettivo
L'obiettivo del progetto è la realizzazione di una piattaforma distribuita per la gestione di giochi da tavolo e da bar, che permetta la creazione di tornei e di raccogliere e analizzare dati sullo svolgimento delle partite tramite l'utilizzo di sensori.

Questo avviene perchè ogni gioco ha dei sensori che rilevano gli input significativi (i gol in un calcetto ad esempio), questi vengono convertiti dalla board sul gioco (ad esempio un ESP32) in chiamate http e mqtt al broker o al server locale. 
Prima di poter giocare è necessario autenticarsi o creare un utente.

### Utenti di sistema
Ci sono quattro tipi di utenti:

1) PLAYER -> il giocatore
2) LOCAL_ADMIN -> l'admin del locale (il bar)
3) GAME_ADMIN -> l'admin che gestisce i giochi
4) PLATFORM_ADMIN -> il super admin

La tabella descrive le funzionalità di ogni utente

| Permesso                                           | PLAYER | LOCAL_ADMIN | GAME_ADMIN | PLATFORM_ADMIN |
|----------------------------------------------------|:------:|:-----------:|:----------:|:--------------:|
| Partecipa alle partite                             |   ✓    |             |            |       ✓        |
| Consulta proprie statistiche                       |   ✓    |             |            |       ✓        |
| Visualizza i giochi disponibili nei locali         |   ✓    |      ✓      |            |       ✓        |
| Partecipa ai tornei                                |   ✓    |             |            |       ✓        |
| Gestisce i giochi presenti nel proprio locale      |        |      ✓      |            |       ✓        |
| Configura i dispositivi e monitora le partite      |        |      ✓      |            |       ✓        |
| Visualizza statistiche relative al locale          |        |      ✓      |            |       ✓        |
| Definisce nuove tipologie di giochi                |        |             |     ✓      |       ✓        |
| Configura le regole di registrazione delle partite |        |             |     ✓      |       ✓        |
| Gestisce utenti e locali                           |        |             |            |       ✓        |
| Monitora il funzionamento dell'intero sistema      |        |             |            |       ✓        |
| Accede a statistiche globali                       |        |             |            |       ✓        |
| Crazione dei tornei                                |        |             |            |       ✓        |

Maggiori informazioni in [ruoli_utenti](strutture/ruoli_utenti.md)

## 2. STRUTTURA DEL SISTEMA
E' composto da tre componenti:

*   **Central System (L'Hub):** Microservizio Spring Boot responsabile della *Source of Truth* globale: registrazione utenti, aggregazione statistiche cross-building, coordinamento sincronizzazione.

*   **Local Server (Lo Spoke / Edge Node):** Microservizio Spring Boot installato fisicamente in ogni edificio. Funziona come gateway e nodo di persistenza locale. Persiste gli stati dei giochi nel database locale e genera le statistiche localmente (partite completate, in corso, tempo di utilizzo). I dati aggregati delle statistiche vengono poi inviati al Central System.

*   **Endpoint (Game Clients):** Applicazioni client con interfaccia grafica (JavaFX) che comunicano **esclusivamente** con il Local Server del proprio edificio tramite MQTT over TLS. L'uso di MQTT disaccoppia i client dal server ed è compatibile con la futura integrazione ESP32/Arduino.

Per la comunicazione tra le varie parti ci affidiamo sia ad API REST (usate ad esempio per gli accessi) che al protocollo MQTT.

Maggiori infomazioni sulla comunicazione tra le parti in [messages_flow](strutture/messages_flow.md)

Maggiori informazioni sull'architettura in [architettura_proposta](strutture/architettura%20proposta.md) e [architettura_classi](strutture/architettura_classi.md)

### Funzionamento offline
Alla creazione di un utente nuovo, i dati vengono salvati in un database locale e creato un evento nell'outbox.
Ogni transizione (start, pause, resume e end) avviene nel db locale, gli eventi vengono poi publicati tramite MQTT sul broker locale, alla fine della sessione viene publicato un evento GAME_SESSION_COMPLETE. Ragionamento simile è quello per le prenotazioni, vengono infatti create e salvate sul db locale e ogni azione genera un evento. 
In caso di crash del Local Server, il server recupera le sessioni attive o in pausa dal DB, fa un ping delle macchine tramite MQTT locale e se entro 30 secondi non riceve risposta chiude il gioco con un evento GAME_SESSIONE_COMPLETE.

In pratica quando è offline il sistema usa solo il server locale normalmente registrando tutto ciò che accade. Ogni cinque minuti fa un ping al Central System, se lo rileva online, il locale invia tutti gli eventi pending in un unico payload e svuota la coda. A questo punto il server centrale processa gli eventi, aggiorna i dati (statistiche, utenti e prenotazioni).

Maggiori informazioni [local_offline_handling](strutture/local_offline_handling.md)

## 3. GIOCHI, SENSORI E INTERFACCIA LOCALE
Ogni gioco avrà dei sensori specifici per raccogliere le informazioni sull'andamento delle partite, non avendo giochi fisici non abbiamo deciso il posizionamento sui singoli giochi dei sensori ma ne abbiamo simulati alcuni come scacchi o slot machine.

## 4. TORNEI, STATISTICHE E INTERFACCIA UTENTE
I tornei possono essere organizzati dagli utenti PLATFORM_ADMIN e sono ad eliminazione diretta. Sono organizzati su almeno due edifici diversi e sullo stesso gioco. Esistono due varianti: 
* **Individuale** -> usa l'userId del giocatore
* **A squadre** -> viene registrato un team con un teamId e la lista dei giocatori del team

![creazione_torneo.jpeg](schermate-client/creazione_torneo.jpeg)
![tornei.jpeg](schermate-client/tornei.jpeg)

Maggiori informazioni sui tornei qui [gestione_tornei](strutture/gestione_tornei.md)

Le statististiche che vengono mostrate dipendono da l'utente che ha fatto l'accesso:
* Il player ha accesso hai dati sui giochi che ha giocato lui:

![my_match.jpeg](schermate-client/my_match.jpeg)
![my_stat.jpeg](schermate-client/my_stat.jpeg)

* Il local admin ha accesso a tutti i giochi nel suo locale, con indicato se sono disponibili e i punteggi delle partite.

![local_admin_dashboard.jpeg](schermate-client/local_admin_dashboard.jpeg)

* Il Game Admin ha accesso a tuttti i giochi creati, la sua dashboard è un editor che permette di definire nuovi giochi.

![game_admin_dashboard.jpeg](schermate-client/game_admin_dashboard.jpeg)

* Il Platform Admin ha accesso a tutte le statistiche.

![platform_admin_dashboard.jpeg](schermate-client/platform_admin_dashboard.jpeg)

### Componenti accessori
Come già accennato è necessario autenticarsi, è inoltre presente un sistema di prenotazione.

## 5. FASI DI LAVORO
### 5.1 Specifica 
Come già detto l'applicazione è una piattaforma software per la gestione di sale giochi da tavolo/bar disposte in più edifici fisici.
### Casi d'uso
1) __Prenotazione e gioco__ un giocatore autenticato prenota una postazione di gioco libera nel proprio edificio; se la prenotazione non viene utilizzata entro l'orario previsto la macchina viene rilasciata automaticamente. Il giocatore avvia poi la sessione dal client, può metterla in pausa e riprenderla, e alla fine invia il risultato della partita (vincitore, punteggio, esito).

2) __Login e registrazione offline__ un utente si autentica o crea un nuovo account anche quando il Local Server del proprio edificio è isolato dal Central System, grazie alla replica locale delle credenziali e alla firma dei token JWT con una chiave del Local Server stesso.

3) __Monitoraggio degli endpoint e recupero da crash__ il Local Server verifica periodicamente che le postazioni di gioco connesse siano raggiungibili; se una postazione non risponde per più cicli consecutivi, o se il server si riavvia dopo un crash con sessioni rimaste appese, la partita in corso viene chiusa automaticamente e la macchina torna disponibile.

4) __Creazione e gestione di un torneo__ un Platform Admin crea un torneo (individuale o a squadre) su almeno due edifici per uno stesso gioco; i giocatori si iscrivono in autonomia durante la fase di registrazione aperta, viene generato un bracket ad eliminazione diretta e i risultati dei singoli match aggiornano automaticamente il bracket e la classifica.

5) __Sincronizzazione locale-centrale__ tutti gli eventi generati mentre il Local Server è offline (prenotazioni, sessioni di gioco, iscrizioni) vengono accumulati in una coda locale e inviati in blocco al Central System non appena la connettività torna disponibile.

6) __Consultazione statistiche per ruolo__ ogni tipologia di utente visualizza dati differenti: il player le proprie partite e statistiche personali, il local admin lo stato dei giochi e le statistiche del proprio locale, il game admin l'anagrafica dei giochi definiti, il platform admin le statistiche aggregate dell'intera piattaforma.

7) __Definizione di nuove tipologie di gioco__ un Game Admin definisce nuovi tipi di gioco e le relative regole di registrazione delle partite, incluse eventuali varianti a giocatore singolo basate su esito casuale (es. slot machine, roulette).

8) __Acquisizione eventi dai sensori di gioco__ i sensori posizionati su ciascun gioco fisico (gestiti ad esempio da una board ESP32) rilevano gli eventi significativi della partita e li inviano al Local Server tramite chiamate HTTP e MQTT.

__TODO__
* Diagrammi UML dei casi d'uso (i principali)
* Diagramma UML delle classi del dominio


### 5.2 Progettazione
Vengono usati pattern diversi:

* Sul piano della distribuzione usiamo Hub-and-Spoke unito a quello Pub/Sub. Qui il Central System è l'hub e gli spoke i Local Server, quello Pub/Sub è attuato tramite MQTT tra server locale e Game Client.
* Sul piano dei microservizi il sistema è composto da tre microservizi Spring Boot indipendenti (Central System, Local Server, Game Client Emulator),  organizzati come monorepo Maven multi-modulo con moduli condivisi (shared-domain, shared-dto, shared-mqtt) che non dipendono da nessun framework, per evitare duplicazione di codice tra i tre servizi.
* Sul piano del codice interno a ciascun microservizio, viene applicata la Clean Architecture / architettura esagonale (Ports and Adapters): il dominio (le entità e la logica di business) è Java puro, senza dipendenze da Spring o JPA, mentre l'accesso a database, MQTT e REST avviene tramite adapter separati. Questo rispetta il Dependency Inversion Principle e rende il dominio testabile senza framework.

In sintesi, l'architettura si può riassumere così: microservizi distribuiti in pattern hub-and-spoke con comunicazione ibrida REST/MQTT, ciascun servizio internamente strutturato secondo architettura esagonale, con sincronizzazione asincrona basata su outbox pattern per garantire resilienza offline.

__TODO__
* Diagramma dei package, 
* diagramma delle classi di implementazione
* diagramma di sequenza

#### DEFINIZIONE API REST
La piattaforma espone API REST da due microservizi distinti: il Central System (porta 8180) e il Local Server (porta 8181, uno per edificio), entrambi su HTTPS/TLS 1.3.

L'accesso a ogni endpoint è protetto da autenticazione JWT (firmata con chiavi RSA proprie di ciascun nodo, non intercambiabili tra Central e Local) e regolato tramite RBAC sui quattro ruoli PLAYER, LOCAL_ADMIN, GAME_ADMIN, PLATFORM_ADMIN, con controlli aggiuntivi di self-check sull'utente e di binding sull'edificio dove previsto. Gli endpoint /internal/**, usati solo per la sincronizzazione server-to-server tra Local e Central, sono invece protetti da una API Key condivisa e non richiedono JWT.

La documentaione completa di ogni endpoint è consultabile nel documento [report_api_rest.md](strutture/report_api_rest.md)

#### TOPIC MQTT
Tutti i topic seguono lo schema gerarchico `building/{buildingId}/game/{gameId}/{action}`, ad eccezione di `alerts` che è a livello di edificio (`building/{buildingId}/alerts`, senza `gameId`).

| Topic | Publisher | Subscriber | QoS / Retained | Descrizione |
|---|---|---|---|---|
| `building/{buildingId}/game/{gameId}/state` | Local Server | Game Client (wildcard `+`) | QoS 1, Retained | Stato della macchina di gioco: AVAILABLE, RESERVED, LOBBY, IN_USE |
| `building/{buildingId}/game/{gameId}/session/start` | Game Client | Local Server | QoS 1 | Avvio sessione di gioco (walk-in, con `reservationId` opzionale) |
| `building/{buildingId}/game/{gameId}/session/pause` | Game Client | Local Server | QoS 1 | Pausa della sessione in corso |
| `building/{buildingId}/game/{gameId}/session/resume` | Game Client | Local Server | QoS 1 | Ripresa della sessione |
| `building/{buildingId}/game/{gameId}/session/end` | Game Client | Local Server | QoS 1 | Chiusura sessione con `result_data` (vincitore, punteggio, esito) |
| `building/{buildingId}/game/{gameId}/session/lobby/create` | Game Client (creator) | Local Server | QoS 1 | Creazione di una lobby su un gioco |
| `building/{buildingId}/game/{gameId}/session/lobby/join` | Game Client (joiner) | Local Server | QoS 1 | Ingresso di un giocatore in una lobby esistente |
| `building/{buildingId}/game/{gameId}/session/lobby/start` | Game Client (creator) | Local Server | QoS 1 | Avvio della partita dalla lobby |
| `building/{buildingId}/game/{gameId}/session/lobby/cancel` | Game Client (creator) | Local Server | QoS 1 | Annullamento della lobby prima dell'avvio |
| `building/{buildingId}/game/{gameId}/heartbeat` | Game Client / Local Server | Local Server / Game Client | QoS 0 | Battito periodico del client, oppure PING del server ogni 5 minuti |
| `building/{buildingId}/game/{gameId}/heartbeat/ack` | Local Server / Game Client | Game Client / Local Server | QoS 0 | Risposta (ACK/PONG) al battito ricevuto |
| `building/{buildingId}/game/{gameId}/session/move` | Game Client / tavolo fisico | Local Server | QoS non specificato | Evento di mossa durante la partita (generico, da standardizzare per tipo di gioco) |
| `building/{buildingId}/game/{gameId}/session/score` | Game Client / tavolo fisico | Local Server | QoS non specificato | Evento di punteggio (es. goal a calciobalilla, generico) |
| `building/{buildingId}/game/{gameId}/session/turn` | Game Client / tavolo fisico | Local Server | QoS non specificato | Cambio turno (generico) |
| `building/{buildingId}/alerts` | Local Server | Central System / dashboard | QoS non specificato | Allarmi: client irraggiungibile dopo 3 heartbeat mancati (15 min), prenotazione non valida, ecc. |

