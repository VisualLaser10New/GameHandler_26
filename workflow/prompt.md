Sei un ingegnere del software specializzato in Java, Clean Architecture (Hexagonal Ports and Adapters) e sistemi distribuiti resilienti offline.
Leggi il file workflow.md e il file architettura_classi.md per comprendere il workflow di sviluppo del progetto.
Il tuo compito è implementare solo i seguenti elementi del workflow:

>> [5.9, 5.10]

Per svolgere questo compito con successo, devi attenerti rigorosamente alle specifiche architetturali e seguire passo dopo passo il protocollo di implementazione descritto di seguito.

---

## 1. FASE DI ANALISI E CONTESTUALIZZAZIONE (LETTURA)

Prima di scrivere codice, esegui i seguenti controlli:
1. **Identifica il Modulo e il Package**: Identifica in quale sotto-modulo Maven risiede l'elemento (es. `shared-domain`, `local-server`, `central-system`) e rispetta rigorosamente il package di destinazione.
2. **Backward & Forward Compatibility**:
    - Cerca nel codebase (usando strumenti di ricerca testuale) se esistono già riferimenti a questa classe/interfaccia o a classi correlate.
    - Assicurati che l'implementazione non rompa alcuna firma di metodo preesistente o DTO associato.
3. **Analisi delle Dipendenze Successive**: Verifica quali elementi successivi del workflow dipenderanno da questo codice. Implementa firme pulite, estensibili e conformi ai contratti architetturali definiti nei documenti `architettura_classi.md` e `aggiunta_giochi_azzardo.md`.

---

## 2. REGOLE ARCHITETTURALI E LIMITI DEL MODULO (ISOLATION RULES)

Rispetta rigorosamente l'isolamento dei livelli della Clean Architecture:
*   **Se l'elemento è in `shared-domain` o nel package `domain/` di un microservizio**:
    - Deve essere codice Java PURO.
    - **VIETATA** qualsiasi annotazione di framework (NO Spring `@Component`/`@Service`, NO JPA `@Entity`/`@Table`/`@Column`, NO Jackson o librerie esterne non standard, ad eccezione delle annotazioni Jackson necessarie su `GameResult` polimorfici).
    - Qualsiasi dipendenza temporale deve essere gestita passando un'istanza di `java.time.Clock` come parametro dei metodi, garantendo testabilità deterministica.
*   **Se l'elemento è un Adapter (`infrastructure/adapters/`)**:
    - Gestisci esplicitamente la conversione tra modello di dominio e modello JPA/REST utilizzando i Mapper dedicati (non mescolare mai i due mondi).
    - Gli Adapter devono implementare una porta di dominio (`ports/out/` o `ports/in/`).

---

## 3. PROTOCOLLO DI IMPLEMENTAZIONE (SCRITTURA)

Implementa il codice assicurandoti di coprire:
1. **Eccezioni Dogmatiche di Dominio**:
    - Non lanciare `RuntimeException` generiche.
    - Utilizza o crea le eccezioni specifiche all'interno del package `domain/exception/` del rispettivo modulo (es. `GameNotAvailableException`, `UserNotFoundException`).
2. **Thread-Safety e Concorrenza**:
    - Se il codice viene eseguito da task schedulati (es. `SyncSchedulerService`, `ReservationExpirationService`), da listener MQTT asincroni o da controller concorrenti, assicura la thread-safety dello stato.
    - Usa strutture dati concorrenti (es. `ConcurrentHashMap`) o transazioni atomiche (`@Transactional` a livello di service) per prevenire race condition.
3. **Atomicità delle Transazioni (Outbox Pattern)**:
    - Se l'operazione prevede la notifica di eventi o la sincronizzazione (es. salvataggio prenotazione o fine sessione), assicurati che la scrittura dell'entità principale e la scrittura nella tabella `outbox_events` avvengano all'interno della **stessa transazione atomica** per evitare disallineamenti di stato.

---

## 4. FASE DI VERIFICA (COMPILAZIONE E TEST)

A implementazione completata, esegui le seguenti verifiche:
1. **Compilazione Modulare**:
    - Esegui una compilazione pulita focalizzata esclusivamente sul modulo modificato usando il comando:
      `mvn clean compile -pl :[nome-modulo-maven]` (es. `mvn clean compile -pl :shared-domain`).
    - Assicurati che non vi siano warning o errori di compilazione.
2. **Corrispondenza 1:1**:
    - Verifica che tutte le firme dei metodi implementati corrispondano esattamente alla specifica del sottopunto in `workflow.md`.
3. **Report di Completamento**:
    - Fornisci una sintesi del codice scritto, specificando i file modificati o creati e il risultato della compilazione.

---

## 5. GESTIONE DI AMBIGUITÀ, IMPREVISTI E DOCUMENTAZIONE

Durante l'implementazione, qualora emergessero discrepanze, ambiguità o problemi tecnici non previsti nei documenti di specifica, segui rigorosamente questo protocollo:
1. **Verifica delle Ambiguità**:
    - Identifica e analizza la discrepanza tra quanto proposto nei documenti di specifica/architettura e le necessità pratiche o i problemi emersi durante l'implementazione.
2. **Analisi dell'Impatto e Ricerca di Soluzioni**:
    - Esamina il codebase circostante per comprendere l'utilizzo effettivo o previsto del componente critico.
    - Elabora e confronta diverse soluzioni architetturali alternative.
    - Seleziona e implementa la soluzione migliore, privilegiando la stabilità, l'estensibilità e la conformità ai pattern di Clean Architecture del progetto.
3. **Aggiornamento della Documentazione**:
    - Informami su come aggiornerai la documentazione di progetto presente nella cartella `workflow/` (es. `architettura_classi.md`, `problemi_noti.md`, ecc.) per riflettere le modifiche strutturali o le scelte implementative effettuate, garantendo che i documenti rimangano sempre allineati al codice reale.
    - **Richiedi la mia approvazione**: Non scrivere codice o documentazione di aggiornamento prima della mia approvazione



# altri prompt

## analisi dei sistemi server distribuiti

Sei un ingegnere del software specializzato in Java, Clean Architecture (Hexagonal Ports and Adapters) e sistemi distribuiti resilienti offline.
Il nostro compito ora è di analizzare e rendere pienamente funzionante la comunicazione tra local-server e central sytem. Analizza entrambi i sistemi per comprendere per ora solo il loro funzionamento. Non modificare codice

crea un piano di risoluzione dei bugs e implementazione delle funzionalità rilevate mancanti:
- il piano deve essere completo
- il piano deve essere altamente ingegnerizzato e rispettare i principi della architettura clean
- il piano deve essere verificato al completamente eseguendo entrambi i sistemi e verificando che non crashino
- il piano deve essere verificato attraverso dei test appositi (utilizzando anche i test già esistenti)
- il piano deve prevedere a priori quali saranno tutti i tipi di messaggi scambiabili tra local e central system e controllare attentamente il codice di essi, analizzando se ad ogni messaggio da una parte, dall'altra ci sia una implementazione che sia ingrado di riceverlo e processarlo evitando bugs
- il piano deve provare che non vi siano assolutamente bottleneck, rallentamenti di comunicazione, deadlock, desincronizzazioni tra i sistemi
  Al termine del piano bisogna verificare la sua funzionalità completa nel seguente modo:
- si analizzano i messaggi inviabili e il codice che li gestisce
- si esegue una simulazione virtuale (anche attraverso test junit) passo passo
- si trovano le eventuali problematiche
- si rileva la causa sorgente primaria dei bugs, e se vi sono possibili altri malfunzionamenti introdotti dallo stesso bug, o se vi è lo stesso bug in altre sezioni del codice
- si applica la patch di correzione, che deve essere una soluzione permanente e alla radice assoluta del problema
Per leggere il codice utilizza diversi subagent


## Verifica e termine implementazione del piano
È stato appena implementato l'intero piano @race_condition_analisys_central_local.md.
Leggi il file e i sorgenti non ancora committati.
Verifica la sua funzionalità completa nel seguente modo:
- si analizzano i messaggi inviabili e il codice che li gestisce
- si esegue una simulazione virtuale (anche attraverso test junit) passo passo, per verificare tutta la logica implementata
- si trovano le eventuali problematiche, bug o errori di: logica e/o sintassi
- si rileva la causa sorgente primaria dei bugs, e se vi sono possibili altri malfunzionamenti introdotti dallo stesso bug, o se vi è lo stesso bug in altre sezioni del codice
- si applica la patch di correzione, che deve essere una soluzione permanente e alla radice assoluta del problema

Infine esegui il central-system e il local-server (non da docker):
- verifica gli output delle esecuzioni
- se ci sono errori, trova la causa sorgente, verifica il codice inerente e applica la patch di correzione
  (Ogni patch deve essere di correzione della causa sorgente primaria e non temporanea. Inoltre verifica che la patch non abbia effetto su altre parti del codice o se ci sono altre parti del codice che presentano le stesse problematiche)
  
- Riesegui i due sistemi e verifica che non crashino, altrimenti ripeti il processo
- Se non ci sono errori, termina il processo e riporta un output di ciò che è stato verificato e risolto, mostrando qual è la situazione attuale del sistema.

Vincoli:
- utilizza obbligatoriamente i subagents per ogni simulazione, esecuzione, write, read dei file.

## Verifica esecuzione codice e scambio messaggi
run the local-server and the central-system (not on docker). 
inspect their outputs, and check if match with the expected execution flows by reading the source code. 
Strictly use only subagent to think, read files and execute code by splitting the job into multiple tasks and delegating them