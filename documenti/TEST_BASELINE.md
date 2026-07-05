# Test Baseline (prima dei fix)

Data: 2026-07-05

## Ambiente
- Maven 3.9.11, Java 21 (Oracle)
- OS: Windows 10

## Risultati
- **central-system**: 177 test, 0 fallimenti, 0 errori, 0 skipped — BUILD SUCCESS
- **local-server**: 527 test, 0 fallimenti, 0 errori, 0 skipped — BUILD SUCCESS
- **shared-domain/dto/mqtt**: compilazione OK

## Note
- I test `BugL01..BugL09` e `BugC-01/02`, `BUG-AUTH-01`, `BUG-REPL-01`, `BUG-SYNC-01` PASSANO già:
  - significa che i bug documentati da tali regression test sono GIÀ fixati nel codice di produzione attuale.
- I fix del piano si concentreranno quindi su:
  - funzionalità mancanti (auto-registrazione local → central)
  - bug non coperti da test esistenti (sessioni abortite conteggiate come completate)
  - miglioramenti ingegneristici (atomicità outbox, poison isolation REQUIRES_NEW, config-driven scheduler, DLQ, purge, late-registration catch-up)
  - test E2E di integrazione (oggi assenti)

## Avvio applicazione
- Verifica eseguita in FASE 0: build OK su `mvn -pl central-system,local-server -am clean package -DskipTests`.
- Il local-server attualmente crasha all'avvio se `INTERNAL_API_KEY` non è definito (resolved in B2).
