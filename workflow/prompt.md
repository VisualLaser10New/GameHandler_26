Sei un ingegnere del software specializzato in Java, Clean Architecture (Hexagonal Ports and Adapters) e sistemi distribuiti resilienti offline.
Il tuo compito è implementare solo il seguente elemento del workflow (leggi il file workflow.md):

>> [shared-mqtt punto 4.2 e 4.3 del workflow]: [domain/ports/in][domain/ports/out]

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