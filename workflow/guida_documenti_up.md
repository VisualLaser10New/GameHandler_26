# Guida alla Realizzazione dei Documenti di Progetto

Questa guida dettaglia la struttura e i punti chiave da includere nella redazione dei quattro documenti fondamentali per il ciclo di vita del software, basandosi sulle best practice dei progetti di esempio.

Ogni documento deve sempre iniziare con un'intestazione standard contenente:

* **Titolo del Documento e Nome del Progetto**
* **Versione**
* **Data/Periodo di redazione**
* **Autore/Team**

---

## 1. Documento di Visione

Questo documento serve a definire l'ambito generale, gli obiettivi di business e le funzionalità ad alto livello del sistema, allineando tutti gli stakeholder.

* **1. Introduzione**
  * **Scopo:** Spiegare l'obiettivo del documento e cosa definisce (visione, ambito, affidabilità).
  * **Destinatari:** Elencare a chi è rivolto il documento (stakeholder, sviluppatori, manager).
  * **Ambito:** Descrivere brevemente cosa farà il sistema e le sue principali interfacce/componenti.
* **2. Panoramica del Prodotto**
  * **Contesto del Prodotto:** Descrivere l'ambiente in cui il sistema opererà e la sua architettura ad alto livello.
  * **Funzionalità del Prodotto:** Elencare in modo discorsivo le macro-funzionalità offerte.
  * **Caratteristiche degli Utenti:** Definire i profili dei vari attori (es. utente primario, amministratore) e le loro necessità.
  * **Vincoli:** Specificare i limiti del sistema categorizzati per Performance, Affidabilità, Sicurezza e Usabilità.
* **3. Funzionalità del Prodotto**
  * Creare una tabella riassuntiva delle funzionalità chiave includendo: ID, Nome Funzionalità, Descrizione e Livello di Priorità.
* **4. Assunzioni e Dipendenze**
  * Elencare i prerequisiti necessari affinché il sistema funzioni (es. hardware, competenze degli utenti, dati iniziali).
* **5. Obiettivi di Business e Criteri di Successo**
  * Definire i traguardi misurabili che determineranno il successo del progetto (es. tempi di risposta, adozione, riduzione errori).
* **6. Stakeholder**
  * Creare una tabella che elenca le parti interessate con le relative colonne: Stakeholder, Ruolo e Interesse specifico nel progetto.
* **7. Rischi**
  * Identificare i possibili ostacoli tecnici o organizzativi che potrebbero minacciare il progetto.
* **8. Miglioramenti Futuri**
  * Elencare funzionalità o espansioni previste per versioni successive.
* **9. Approvazione**
  * Includere una tabella per le firme dei responsabili (Nome e Ruolo).

---

## 2. Documento dei Requisiti

Questo documento traduce la visione in specifiche dettagliate, funzionali e non, necessarie per guidare lo sviluppo e i test.

* **1. Introduzione**
  * **Scopo del documento:** Spiegare che il documento guida progettazione, sviluppo e test.
  * **Ambito del sistema:** Descrivere i processi coperti dal software.
  * **Definizioni, acronimi e abbreviazioni:** Inserire una tabella con la terminologia specifica utilizzata.
* **2. Descrizione generale**
  * **Persone interessate e utenti:** Dettagliare i ruoli degli attori e le loro azioni.
  * **Vincoli e dipendenze:** Specificare limiti tecnici, hardware o di processo.
  * **Presupposti:** Condizioni di partenza affinché il sistema possa operare correttamente.
* **3. Requisiti Funzionali**
  * Fornire una tabella dettagliata dei requisiti derivati dalla Visione, includendo: ID, Nome Requisito, Descrizione e Criterio di Accettazione (fondamentale per i test).
* **4. Requisiti Non Funzionali**
  * Tabellare le specifiche di qualità divise per Categoria (Prestazioni, Sicurezza, Usabilità, Manutenibilità) descrivendo il vincolo da rispettare.
* **5. Diagramma dei Casi d’Uso**
  * Elencare gli Attori, i Casi d'Uso principali (dentro il confine del sistema) e includere il Diagramma UML relativo.
* **6. Descrizione dei Casi d’Uso**
  * Per ogni caso d'uso principale, fornire una scheda dettagliata comprendente: Sviluppatore assegnato, ID, Attori Primari, Precondizioni, Descrizione, Sequenza degli Eventi Principale (passo-passo), Postcondizioni ed Eccezioni.
* **7. Matrice di Mappatura dei Requisiti**
  * Creare una tabella di tracciabilità che incrocia i Requisiti Funzionali (righe) con i Casi d'Uso (colonne) per dimostrare che tutti i requisiti sono coperti.
* **8. Rischi**
  * Riportare i rischi specifici legati all'implementazione dei requisiti.
* **9. Approvazione**
  * Tabella firme.

---

## 3. Documento di Progettazione

Questo documento definisce l'architettura software e modella la struttura del sistema per soddisfare i requisiti.

* **1. Introduzione**
  * Spiegare che il documento dettaglia l'architettura e la progettazione per garantire manutenibilità e soddisfare i requisiti (RF/RNF).
* **2. Architettura del Sistema**
  * **Descrizione Architetturale:** Spiegare lo stile architettonico scelto (es. a strati, microservizi) e descrivere la funzione di ogni livello (es. UI, Business Logic, Data Access).
  * **Diagramma di Architettura:** Inserire uno schema visivo dei livelli e delle dipendenze.
* **3. Progettazione Statica: Diagramma delle Classi**
  * **Descrizione delle Classi e dei Pattern:** Elencare i Design Pattern architetturali scelti e motivarne l'uso in relazione ai requisiti.
  * **Diagramma UML delle Classi:** Inserire il diagramma di classe completo mostrando attributi, metodi e relazioni.
  * **Tabella incrociata Requisiti/Classi**: Definire una tabella booleana (con celle che assumono vero (checkmark) o nullo) che specifica per ogni requisito funzionale, quale classe gestisce il caso specifico. Verificare dunque che tutti i requisiti siano interamente comperti dalle classi; controllare di conseguenza che le classi con i propri metodi siano correttamente progettati in modo tale da coprire tutti i requisiti funzionali.
* **4. Progettazione Dinamica: Diagrammi di Sequenza**
  * Dettagliare l'interazione temporale tra gli oggetti per realizzare tutti i casi d'uso. Inserire un paragrafo descrittivo e il relativo Diagramma di Sequenza per ogni UC analizzato.
* **5. Modellazione dei Processi: Diagrammi di Attività**
  * Modellare il flusso di lavoro (workflow) operativo, le decisioni logiche e i flussi paralleli tramite Diagrammi di Attività.
* **6. Base di dati: Diagramma della Base di Dati**
  * Dettagliare il diagramma della base di dati (in particolar modo se si tratta di diagrammi ER per database relazionali), in modo completo ed esaustivo.
  * Il diagramma deve includere tutte le entità, tutte le relazioni e tutti gli attributi/campi di ogni Entità e Relazione.
  * Il diagramma deve essere completamente ristrutturato e non deve presentare modelli non riproducibili attraverso i linguaggi di database (es. non presenta ereditarietà non risolte).

---

## 4. Documento di Implementazione

Questo documento descrive come la progettazione è stata effettivamente tradotta in codice, illustrando pattern specifici, viste logiche, fisiche e UI.

* **1. Descrizione e Motivazione dei Design Pattern**
  * Elencare in dettaglio i pattern utilizzati (suddivisi in Strutturali, Creazionali, Comportamentali). Per ognuno specificare:
    * **Nome Classe** coinvolta.
    * **Motivazione:** Quale problema di design risolve.
    * **Implementazione:** Come è stato applicato nel codice.
    * **Vantaggio:** Il beneficio ottenuto in termini di clean code e manutenibilità.
* **2. Diagrammi delle Classi per Caso d'Uso (Viste Logiche)**
  * Fornire diagrammi delle classi ridotti e specifici per illustrare le classi coinvolte in un singolo Caso d'Uso.
* **3. Diagramma dei Package (Architettura Software)**
  * Spiegare e mostrare tramite un diagramma come le classi sono organizzate in namespace/layer logici (es. Presentation, Business, Data, Domain).
* **4. Diagramma di Deployment (Architettura Fisica)**
  * Illustrare tramite diagramma come i componenti software (es. file eseguibili, database) sono distribuiti sui nodi hardware fisici.
* **5. Schermate di Mockup (Interfaccia Utente)**
  * Inserire i wireframe o gli screenshot delle interfacce utente sviluppate per mostrare il risultato finale visivo.
