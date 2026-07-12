Sei un ingegnere del software specializzato in Java, Clean Architecture (Hexagonal Ports and Adapters) e sistemi distribuiti resilienti offline.
Leggi il file `PIANO_UTENTI_TORNEI.md` per comprendere il piano di sviluppo del progetto.
Il tuo compito è implementare solo i seguenti elementi del piano:

>> fase 4

Per svolgere questo compito con successo, devi attenerti rigorosamente alle specifiche architetturali e seguire passo dopo passo il protocollo di implementazione descritto di seguito.
Obbligatorio: crea un subagent e passagli l'intero prompt nella sezione <prompt>. Il subagent deve essere obbligato ad eseguire altri subagents per i task in cui il <prompt> lo richiede.
Quando il subagent principale termina, verifica che abbia completato tutti i task richiesti nel piano. Altrimenti avvia un altro subagent per continuare l'implementazione.

<vincoli>
- Quando richiedo di creare i subagent, è obbligatorio crearli. non è opzionale
</vincoli>

<prompt>

## 1. ANALISI E CONTESTUALIZZAZIONE (LETTURA)

Prima di scrivere codice, usa dei subagent per eseguire i seguenti controlli:
1. **Identifica il Modulo e il Package**: Identifica in quale sotto-modulo Maven risiede l'elemento (es. `shared-domain`, `local-server`, `central-system`) e rispetta rigorosamente il package di destinazione.
2. **Backward & Forward Compatibility**:
    - Cerca nel codebase (usando strumenti di ricerca testuale) se esistono già riferimenti a questa classe/interfaccia o a classi correlate.
    - Assicurati che l'implementazione non rompa alcuna firma di metodo preesistente o DTO associato.
3. **Analisi delle Dipendenze Successive**: Verifica quali elementi successivi del piano dipenderanno da questo codice. Implementa firme pulite, estensibili e conformi ai contratti architetturali definiti nei documenti `architettura_classi.md`.

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
Crea dei subagents per ogni task. Tutte le read, le implementazioni devono essere eseguite dai subagents.
Implementa il codice assicurandoti di coprire:
1. **Eccezioni Dogmatiche di Dominio**:
    - Non lanciare `RuntimeException` generiche.
    - Utilizza o crea le eccezioni specifiche all'interno del package `domain/exception/` del rispettivo modulo (es. `GameNotAvailableException`, `UserNotFoundException`).
2. **Thread-Safety e Concorrenza**:
    - Se il codice viene eseguito da task schedulati (es. `SyncSchedulerService`, `ReservationExpirationService`), da listener MQTT asincroni o da controller concorrenti, assicura la thread-safety dello stato.
    - Usa strutture dati concorrenti (es. `ConcurrentHashMap`) o transazioni atomiche (`@Transactional` a livello di service) per prevenire race condition.
3. **Atomicità delle Transazioni (Outbox Pattern)**:
    - Se l'operazione prevede la notifica di eventi o la sincronizzazione (es. salvataggio prenotazione o fine sessione), assicurati che la scrittura dell'entità principale e la scrittura nella tabella `outbox_events` avvengano all'interno della **stessa transazione atomica** per evitare disallineamenti di stato.
4. **Verifica preliminare**: Crea un subagent per verificare inizialmente che i codice scritti dai vari subagents siano corretti e coerenti tra loro.
---

## 4. FASE DI VERIFICA (COMPILAZIONE E TEST)
Crea dei subagents per ogni task. Tutte le read, le implementazioni devono essere eseguite dai subagents.
A implementazione completata, esegui le seguenti verifiche:
1. **Compilazione Modulare**:
    - Esegui una compilazione pulita focalizzata esclusivamente sul modulo modificato usando il comando:
      `mvn clean compile -pl :[nome-modulo-maven]` (es. `mvn clean compile -pl :shared-domain`).
    - Assicurati che non vi siano warning o errori di compilazione.
2. **Corrispondenza 1:1**:
    - Verifica che tutte le firme dei metodi implementati corrispondano esattamente alla specifica del sottopunto in `PIANO_UTENTI_TORNEI.md`.
3. **Report di Completamento**:
    - Fornisci una sintesi del codice scritto, specificando i file modificati o creati e il risultato della compilazione.
    - Mark dei checkbox per ogni task completato nel file `PIANO_UTENTI_TORNEI.md`.
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
    - **Richiedi la mia approvazione**: Non scrivere codice o documentazione di aggiornamento prima della mia approvazione
    - **Aggiorna il file @workflow/architettura_classi.md**: scrivi nuove sezioni per i nuovi componenti o aggiorna la sezione esistente, riporta tutte le scelte prese.

</prompt>

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
È stato appena implementato l'intero piano @PIANO_UTENTI_TORNEI.md.
Leggi il piano.
Leggi il file e i sorgenti non ancora committati.
1. Verifica per ogni sottopunto di ogni fase:
- che il task sia stato completato.
- Se è stato completato, marka il checkbox nel piano

2. Verifica la sua funzionalità completa nel seguente modo:
- si analizzano i messaggi inviabili e il codice che li gestisce
- si esegue una simulazione virtuale (anche attraverso test junit) passo passo, per verificare tutta la logica implementata
- si trovano le eventuali problematiche, bug o errori di: logica e/o sintassi
- si rileva la causa sorgente primaria dei bugs, e se vi sono possibili altri malfunzionamenti introdotti dallo stesso bug, o se vi è lo stesso bug in altre sezioni del codice
- si applica la patch di correzione, che deve essere una soluzione permanente e alla radice assoluta del problema

3. Verifica la rolebased policy:
- verifica leggendo il codice e simulandolo virtualemnte che ogni ruolo possa operare solo dove ha il pemesso
- utenti non autorizzati non devono poter accedere a certi servizi.
- i tipi di utenti sono 4 e queste sono i ruoli a loro associati:
  - Giocatori
      - Partecipano alle partite
      - Possono consultare le proprie statistiche
      - Possono visualizzare i giochi disponibili nei diversi locali
      - Possono partecipare a tornei
  - Amministratori del Locale
      - Gestiscono i giochi presenti nel proprio locale
      - Possono configurare i dispositivi e monitorare le partite
      - Possono visualizzare statistiche relative al locale
  - Amministratori del Gioco
      - Possono definire nuove tipologie di giochi
      - Configurano le regole di registrazione delle partite
  - Amministratori della Piattaforma
      - Gestiscono utenti e locali
      - Monitorano il funzionamento dell'intero sistema
      - Possono accedere a statistiche globali
- se ci sono errori prepara un piano di correzione per risolverli

3. Infine esegui il central-system e il local-server (non da docker):
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


## Aggiunta gui decente
Sei un Enterprise Software Architect specializzato in Clean Architecture, sistemi distribuiti (Central-System / Local-Server) e progettazione di interfacce utente (Client Emulator).

Il tuo compito è la progettazione e pianificazione della **Fase 7 (GUI)** basandoti sul file `PIANO_UTENTI_TORNEI.md` e sul `<goal>` dichiarato sotto.

Opereremo in modalità **STAGED (Multi-passo)**. Non generare il piano di implementazione finale finché non avremo completato le fasi di analisi e allineamento architetturale.

---

<goal>
Deve essere realizzata una interfaccia utente che permetta agli utenti di:
- Visualizzare i giochi disponibili
- Consultare le proprie statistiche
- Vedere le partite giocate
- Visualizzare tornei e classifiche
Gli amministratori (analizzando tutti e 3 i tipi di amministratori esistenti nel sistema) devono avere accesso a funzionalità aggiuntive di gestione.
</goal>

<vincoli>
- L'utilizzo dei subagent non è opzionale: ogni singola operazione di lettura, analisi delle mancanze e mappatura dei flussi sui 3 sistemi DEVE essere delegata a subagent verticali dedicati.
- Tu agisci come coordinatore/orchestratore: raccogli i report dei subagent, verifichi la coerenza con la Clean Architecture e ti interfacci con l'utente per le approvazioni.
- Rispetta rigorosamente la struttura STAGED. Non passare alla fase successiva senza la mia esplicita approvazione.
</vincoli>

---

## FASE 1: GAP ANALYSIS & MAPPING DEI 3 SISTEMI (Analisi delle Mancanze)
*Obbligatorio: Avvia 3 subagent in parallelo per ispezionare lo stato attuale del codebase sui rispettivi moduli.*

Istruisci i subagent a eseguire i seguenti controlli incrociati rispetto al `<goal>`:
1. **Subagent Central-System**: Individua quali API, Use Case (Incomming Ports) e componenti di persistenza mancano nel sistema centrale per supportare statistiche, tornei, classifiche globali e i 3 tipi di amministratori.
2. **Subagent Local-Server**: Individua cosa manca a livello locale per la resilienza offline, la sincronizzazione delle partite giocate, la disponibilità dei giochi e le autorizzazioni dei gestori locali.
3. **Subagent Client Emulator (GUI)**: Identifica lo stato attuale dei componenti grafici e mappa le viste necessarie per l'utente standard e per le dashboard differenziate dei 3 tipi di amministratori.

**OUTPUT RICHIESTO AL COORDINATORE PER LA FASE 1:**
Presentami un report dettagliato sulle mancanze (Gap Analysis) e sui punti di ambiguità riscontrati tra la specifica del goal e l'architettura attuale. *Fermati qui e attendi le mie decisioni.*

---

## FASE 2: DEFINIZIONE DEI CONTRATTI E FLUSSI DATI (Architectural Design)
*Nota: Questa fase si attiva solo dopo l'approvazione della Fase 1.*
*Obbligatorio: Avvia un subagent per definire le interfacce e i contratti DTO.*

Istruisci il subagent a tracciare come i dati fluiranno dalla GUI (Client Emulator) attraverso il Local Server fino al Central System, rispettando la Clean Architecture:
* Definisci i contratti delle nuove porte di input/output necessarie nei backend.
* Specifica i meccanismi di autenticazione/autorizzazione per distinguere i 3 livelli di amministrazione nella GUI.

Nessun output richeisto

---

## FASE 3: GENERAZIONE DEL PIANO IN MARKDOWN (Roadmap Esecutiva)
*Nota: Questa fase si attiva solo dopo l'approvazione del Design Architetturale.*
*Obbligatorio: Avvia un subagent specializzato in technical writing e project planning.*

Istruisci il subagent a generare il piano operativo definitivo. Il piano deve rispettare i seguenti criteri:
1. Deve essere formattato in **Markdown**.
2. Deve suddividere i compiti in sezioni nette e non sovrapposte per: **Central System**, **Local Server** e **Client Emulator (GUI)**.
3. Deve includere tutti i componenti infrastrutturali e di dominio mancanti individuati nelle fasi precedenti.
4. **Struttura a Checkbox**: Deve contenere un elenco puntato multiplo e gerarchico (sotto-punti dettagliati) con checkbox (`- [ ]`) pronto per essere integrato o tracciato in `PIANO_UTENTI_TORNEI.md`.

**OUTPUT RICHIESTO AL COORDINATORE PER LA FASE 3:**
Fornisci il piano Markdown completo e dettagliato pronto per l'esecuzione incrementale.



---

# Coding prompt, extremely engineerized

You are a software engineer specializing in Java, Clean Architecture (Hexagonal Ports and Adapters), and offline-resilient distributed systems.
Read the file `PIANO_UTENTI_TORNEI.md` to understand the project's development plan.
Your task is to implement only the following items from the plan:

>> Fase 6

To successfully complete this task, you must strictly adhere to the architectural specifications and follow the implementation protocol described below step-by-step.
Mandatory: Create a subagent and pass the entire prompt to it inside the <prompt> section.

<constraints>
- When I request the creation of subagents, it is mandatory to create them. It is not optional.
- You act as a coordinator/orchestrator: you gather reports from subagents, verify consistency, and interface with the user for approvals.
- Strictly respect the STAGED (Multi-step) structure described below. Do not proceed to the next phase unless the previous one has been approved by the user.
</constraints>
<prompt>

## STEP 1: BEHAVIOR SPEC & DUPLICATION MAP (Analysis and Reading)
*Mandatory: Launch dedicated parallel subagents to analyze the codebase.*

Instruct the subagents to perform the following checks before writing any code:
1. **Identify the Module and Package**: Identify in which Maven sub-module the element resides (e.g., `shared-domain`, `local-server`, `central-system`) and strictly respect the target package.
2. **Backward & Forward Compatibility**:
    - Search the codebase (using text search tools) to check if references to this class/interface or related classes already exist to avoid duplication.
    - Ensure that the implementation does not break any pre-existing method signatures or associated DTOs.
3. **Subsequent Dependency Analysis**: Check which subsequent items in the plan will depend on this code. Implement clean, extensible signatures that comply with the architectural contracts defined in the `architettura_classi.md` documents.
4. **Ambiguity Verification**: Identify and analyze every single discrepancy, omission, or unforeseen technical issue between what is proposed in the specification/architecture documents and the practical needs that arise.

**OUTPUT REQUIRED FROM THE COORDINATOR FOR PHASE 1:**
Gather the subagents' reports and present me with a detailed analysis of the ambiguities found, the impact on the surrounding code, and the possible design options.


## STEP 2: MODULE PLAN & ARCHITECTURAL ISOLATION (Contract Design)
*Note: This phase is activated only after the green light on Phase 1.*
*Mandatory: Launch a subagent to design the architectural skeleton.*

Instruct the subagent to define the structural layout without writing the internal logic, strictly applying the module isolation rules:
* **If the element is in `shared-domain` or within the `domain/` package of a microservice**:
    - It must be PURE Java code.
    - Any framework annotations are **FORBIDDEN** (NO Spring `@Component`/`@Service`, NO JPA `@Entity`/`@Table`/`@Column`, NO Jackson or non-standard external libraries, with the exception of necessary Jackson annotations on polymorphic `GameResult` types).
    - Any temporal dependencies must be handled by passing an instance of `java.time.Clock` as a method parameter, ensuring deterministic testability.
* **If the element is an Adapter (`infrastructure/adapters/`)**:
    - Explicitly handle the conversion between the domain model and the JPA/REST model using dedicated Mappers (never mix the two worlds).
    - Adapters must implement a domain port (`ports/out/` or `ports/in/`).

**OUTPUT REQUIRED FROM THE COORDINATOR FOR PHASE 2:**
No output is required. Automatically proceed.


## STEP 3: INCREMENTAL IMPLEMENTATION (Atomic Writing and Concurrency)
*Note: This phase is activated only after the approval of the Module Plan.*
*Mandatory: Launch distinct subagents for the atomic writing of each component.*

Instruct the subagents to implement the internal logic, making sure to cover:
1. **Dogmatic Domain Exceptions**:
    - Do not throw generic `RuntimeException`s.
    - Use or create specific exceptions within the `domain/exception/` package of the respective module (e.g., `GameNotAvailableException`, `UserNotFoundException`).
2. **Thread-Safety and Concurrency**:
    - If the code is executed by scheduled tasks (e.g., `SyncSchedulerService`, `ReservationExpirationService`), async MQTT listeners, or concurrent controllers, ensure the thread-safety of the state.
    - Use concurrent data structures (e.g., `ConcurrentHashMap`) or atomic transactions (`@Transactional` at the service level) to prevent race conditions.
3. **Transaction Atomicity (Outbox Pattern)**:
    - If the operation involves event notification or synchronization (e.g., saving a reservation or session end), ensure that the writing of the main entity and the writing into the `outbox_events` table occur within the **same atomic transaction** to avoid state misalignments.


## STEP 4: REGRESSION CHECKS & VERIFICATION (Compilation and Testing)
*Mandatory: Launch a final QA/Verification subagent.*

The verification subagent must execute the following steps:
1. **Preliminary Verification**: Check that the code written by the various subagents in Phase 3 is correct, consistent with one another, and free of internal conflicts.
2. **Modular Compilation**:
    - Run a clean compilation focused exclusively on the modified module using the command:
      `mvn clean compile -pl :[maven-module-name]` (e.g., `mvn clean compile -pl :shared-domain`).
    - Ensure there are no compilation warnings or errors.
3. **1:1 Match**:
    - Verify that all implemented method signatures exactly match the specification of the sub-item in `PIANO_UTENTI_TORNEI.md`.

**OUTPUT REQUIRED FROM THE COORDINATOR FOR PHASE 4:**
1. Provide the final report from the QA subagent with a summary of the written code, specifying the modified or created files and the compilation outcome.
2. Mark the checkboxes for each completed task in the `PIANO_UTENTI_TORNEI.md` file.
3. **Update the `@workflow/architettura_classi.md` file**: Only after my final approval of the code, write the new sections for the new components or update the existing section, documenting all the design choices made.

</prompt>
