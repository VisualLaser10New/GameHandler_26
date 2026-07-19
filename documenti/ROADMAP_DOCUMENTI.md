<div align=center>
<h1> GAME_HANDLER </h1>
</div>

### Obiettivo
L'obiettivo del progetto è la realizzazione di una piattaforma distribuita per la gestione di giochi da tavolo e da bar, che permetta di raccogliere e analizzare dati sullo svolgimento delle partite tramite l'utilizzo di sensori e creare tornei.

Una visione più approfondita degli obiettivi del progetto è consultabile nel file [VISION.md](VISION.md)

Noi nel nostro progetto non abbiamo realizzato una versione fisica dei giochi ma li simuliamo via software, qualora si volesse costruire un gioco reale, i sensori comunicheranno a una board (come un raspberry) che convertirà gli input in chiamate http e mqtt al broker e al local server.

### Funzionamento
I giochi sono dotati di sensori che rilevano gli eventi significativi delle partite. I vari giochi sono connessi a un server locale (gestito ad esempio dal proprietario del bar) che a sua volta è connesso a un server centrale (di proprietà ad esempio del produttore dei giochi).
Il sistema permette di creare tornei e, tramite le informazioni rilevate dai sensori, ottenere statistiche sull’andamento delle partite.

Tutti i requisiti di sistema sono definiti nel documento [REQUIREMENTS.md](REQUIREMENTS.md)

### Utenti di sistema
Un utente deve autenticarsi o creare un account prima di poter giocare. Esistono quattro ruoli possibili:
* PLAYER
* LOCAL_ADMIN
* GAME_ADMIN
* PLATFORM_ADMIN

Le informazioni riguardo agli utenti si trovano in [ruoli_utenti.md](strutture/ruoli_utenti.md)

### Architettura
Il sistema è organizzato secondo un'architettura distribuita, composta da un Central System, uno o più Local Server e i Game Client. Nei vari documenti linkati di seguito vengono mostrate le tecnologie utilizzate, la struttura dei moduli software e delle classi, i pattern architetturali utilizzati e i flussi di comunicazione tra i componenti.

Il documento di design è consultabile nel file [DESIGN.md](DESIGN.md)

L'architettura proposta è consultabile nel file [architettura proposta.md](strutture/architettura%20proposta.md)

L'architettura delle classi è consultabile nel file [architettura_classi.md](strutture/architettura_classi.md)

Lo scambio di messaggi tra le varie parti è documentato in [messages_flow.md](strutture/messages_flow.md)

### Sicurezza
La piattaforma utilizza protocolli di comunicazione sicuri e un'infrastruttura basata sui certificati per garantire autenticazione e cifratura delle comunicazioni tra i vari componenti distribuiti.

La documentazione è disponibile nel file [certificates_structure.md](strutture/certificates_structure.md)

### Configurazione di rete

L'installazione della piattaforma richiede una configurazione consistente e coordinata della rete e dei servizi distribuiti.

La configurazione di indirizzi IP, porte e servizi è documentata in [indirizzamento_ip.md](strutture/indirizzamento_ip.md)

### Implementazione
L'implementazione del progetto è accompagnata da una documentazione tecnica che descrive come configurare l'ambiente di sviluppo, compilare il software, eseguire i test e contribuire allo sviluppo del progetto.

Il documento in cui è descritta l'implementazione è [IMPLEMENTATION.md](IMPLEMENTATION.md)

Ci sono poi due documenti che approfondiscono funzionalità specifiche:
[implementazione_iot.md](strutture/implementazione_iot.md), che è una guida pratica per gli sviluppatori e copre (dal punto 14) l'integrazione futura della piattaforma con dispositivi IoT e sensori.

[local_offline_handling.md](strutture/local_offline_handling.md), che descrive il comportamento del sistema quando è disconnesso dal server locale.

### Funzionalità della piattaforma
Qui vengono descritte alcune funzionalità specifiche quali i tornei e la gestione di giochi d'azzardo con un solo giocatore come le slot machine:

[gestione_tornei.md](strutture/gestione_tornei.md) 

[aggiunta_giochi_azzardo.md](strutture/aggiunta_giochi_azzardo.md)

### Test e validazione
Il software viene testato attraverso una suite completa di test funzionali e mediante un'analisi delle presatazioni di sistema:

[TEST_BASELINE.md](TEST_BASELINE.md) contiene i risultati dei test automatici e delle regressioni effettuate sul progetto.

[PERF_ANALYSIS.md](PERF_ANALYSIS.md) riporta le analisi delle prestazioni, della concorrenza e del sistema sotto carico.
