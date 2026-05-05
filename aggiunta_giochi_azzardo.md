Viewed architettura_classi.md:647-654

## Analisi: Supporto Slot Machine e Roulette

### Caratteristiche di questi giochi

| Gioco | Giocatori | Turni | Punteggio | Risorse | Board | Peculiarità |
|---|---|---|---|---|---|---|
| **Slot Machine** | 1 (solitario) | No | No | Sì (crediti) | No | Spin → risultato casuale, payout ratio |
| **Roulette** | 1+ (indipendenti) | Sì (round) | No | Sì (fiches) | Sì (tavolo) | Puntate multiple, numero estratto, payoff calcolato |

Questi giochi differiscono dagli attuali perché sono **giochi d'azzardo a giocatore singolo/indipendente** (non competitivi), basati su **economia crediti/fiches** con risultati generati da **RNG** (Random Number Generator).

---

### Livello 1: `shared-domain` — Nessuna modifica strutturale

La gerarchia di interfacce attuale **copre già** entrambi i casi:

| Gioco | Interfacce applicabili | Motivazione |
|---|---|---|
| **Slot** | `ScoredGame` (oppure `ResourceBasedGame`) | Il credito è una risorsa singola; lo spin è un'azione che modifica il saldo |
| **Roulette** | `ResourceBasedGame`, `TurnBasedGame` | Fiches = risorsa, ogni round = turno con puntate |

**Nessuna nuova interfaccia necessaria.** L'unica aggiunta sarebbe nelle **implementazioni concrete** e nei **record di risultato**:

```java
// Nuovi record in shared-domain/result/
public record SlotResult(String visitorId, int totalSpins, int creditsIn,
                          int creditsOut, int biggestWin,
                          WinCondition winCondition) implements GameResult {}

public record RouletteResult(String visitorId, int totalRounds,
                              int totalBetAmount, int totalPayout,
                              List<String> winningNumbers,
                              WinCondition winCondition) implements GameResult {}
```

E un aggiornamento all'enum `GameType`:
```java
public enum GameType {
    CHESS, FOOSBALL, DARTS, MONOPOLY, RISK,
    SLOT_MACHINE, ROULETTE   // ← aggiunte
}
```

E ai `@JsonSubTypes` di `GameResult`:
```java
@JsonSubTypes.Type(value = SlotResult.class, name = "SLOT_MACHINE"),
@JsonSubTypes.Type(value = RouletteResult.class, name = "ROULETTE"),
```

> **Questi sono gli unici 3 punti di modifica** al codice condiviso. Il principio **Open/Closed** è rispettato: si aggiungono classi, non si modificano le esistenti (tranne l'enum e l'annotazione, che sono punti di estensione previsti by design).

---

### Livello 2: `shared-dto` / `shared-mqtt` — Zero modifiche

I DTO e i payload MQTT sono **già generici**. `GameSessionDto` trasporta `resultData` come stringa JSON. `SessionEndPayload` trasporta `resultData` come JSON. **Nessun DTO nuovo necessario.**

---

### Livello 3: `local-server` — Zero modifiche

- Il `GameSessionService` lavora con `GameResult` (interfaccia). Non conosce i sottotipi.
- Il `GameSessionMapper` deserializza il JSON tramite `@JsonTypeInfo` → Jackson risolve automaticamente `SlotResult` o `RouletteResult`.
- Lo schema DB **non cambia**: la colonna `result_data JSON` accoglie qualsiasi struttura.
- Le statistiche (`StatisticsService`) aggregano per `game_type` → funzionano automaticamente.

**Zero classi da toccare, zero tabelle da alterare.**

---

### Livello 4: `central-system` — Zero modifiche

Stessa logica del local-server: il `SyncReceiverService` e il `StatisticsAggregationService` lavorano su colonne native (`game_type`, `duration_s`, `winner_id`) e ignorano il contenuto specifico di `result_data`.

---

### Livello 5: `game-client-emulator` — Solo aggiunte

Le uniche modifiche sono nel client:

| Classe | Azione | Dettaglio |
|---|---|---|
| `SlotMachineGame` | **NUOVA** | `implements ScoredGame` — gestisce crediti, spin, payout |
| `RouletteGame` | **NUOVA** | `implements ResourceBasedGame, TurnBasedGame` — gestisce fiches, puntate, round |
| `GameFactory` | **Modifica** | Aggiungere `case SLOT_MACHINE → new SlotMachineGame(...)` e `case ROULETTE → new RouletteGame(...)` |
| `GamePlayView` | **Nessuna** | Già generico: mostra punteggio/risorse in base all'interfaccia implementata |

---

### Riepilogo Impatto

| Layer | File modificati | File nuovi | Tabelle DB alterate |
|---|---|---|---|
| `shared-domain` | 2 (`GameType` enum, `GameResult` annotazione) | 2 (`SlotResult`, `RouletteResult`) | 0 |
| `shared-dto` | 0 | 0 | 0 |
| `shared-mqtt` | 0 | 0 | 0 |
| `central-system` | 0 | 0 | 0 |
| `local-server` | 0 | 0 | 0 |
| `game-client-emulator` | 1 (`GameFactory`) | 2 (`SlotMachineGame`, `RouletteGame`) | 0 |
| **TOTALE** | **3** | **4** | **0** |

### Conclusione

L'architettura è **già pronta** per supportare slot e roulette. La strategia MySQL+JSON, la gerarchia di interfacce polimorfiche, e la serializzazione Jackson con `@JsonTypeInfo` fanno sì che **nessun componente infrastrutturale debba cambiare**. L'unico lavoro è aggiungere 4 classi nel layer di dominio (2 result + 2 implementazioni gioco) e aggiornare 3 punti di estensione previsti. Questo conferma che l'**Open/Closed Principle** è rispettato correttamente nell'architettura attuale.