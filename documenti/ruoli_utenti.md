# Matrice ruoli e visibilità navigazione

Questa matrice descrive quali ruoli possono accedere a ciascuna pagina del Game Client Emulator (JavaFX) e, di conseguenza, quali pulsanti di navigazione la navbar mostra dopo il login.

I 4 ruoli sono definiti in `shared/shared-domain/.../security/Role.java:37-41`:

- **PLAYER** — Giocatore
- **LOCAL_ADMIN** — Amministratore del Locale
- **GAME_ADMIN** — Amministratore del Gioco
- **PLATFORM_ADMIN** — Amministratore della Piattaforma (superuser)

Spec funzionale di riferimento: `documenti/PIANO_UTENTI_TORNEI.md:799-800`.

---

## 1. Permessi funzionali per ruolo

| Permesso | PLAYER | LOCAL_ADMIN | GAME_ADMIN | PLATFORM_ADMIN |
|---|:---:|:---:|:---:|:---:|
| Partecipa alle partite | ✓ | | | ✓ |
| Consulta proprie statistiche | ✓ | | | ✓ |
| Visualizza i giochi disponibili nei locali | ✓ | ✓ | | ✓ |
| Partecipa ai tornei | ✓ | | | ✓ |
| Gestisce i giochi presenti nel proprio locale | | ✓ | | ✓ |
| Configura i dispositivi e monitora le partite | | ✓ | | ✓ |
| Visualizza statistiche relative al locale | | ✓ | | ✓ |
| Definisce nuove tipologie di giochi | | | ✓ | ✓ |
| Configura le regole di registrazione delle partite | | | ✓ | ✓ |
| Gestisce utenti e locali | | | | ✓ |
| Monitora il funzionamento dell'intero sistema | | | | ✓ |
| Accede a statistiche globali | | | | ✓ |

---

## 2. Matrice di visibilità della navbar (client JavaFX)

Implementata in `game-client-emulator/.../infrastructure/ui/NavbarController.java`.

| Pagina (pulsante navbar) | PLAYER | LOCAL_ADMIN | GAME_ADMIN | PLATFORM_ADMIN |
|---|:---:|:---:|:---:|:---:|
| Games | ✓ | ✓ | ✗ | ✓ |
| My Stats | ✓ | ✗ | ✗ | ✓ |
| My Matches | ✓ | ✗ | ✗ | ✓ |
| Tournaments | ✓ | ✗ | ✗ | ✓ |
| Aggregated Stats | ✗ | ✓ | ✗ | ✓ |
| Local Dashboard | ✗ | ✓ | ✗ | ✓ |
| Game Admin | ✗ | ✗ | ✓ | ✓ |
| Platform Admin | ✗ | ✗ | ✗ | ✓ |
| Admin Requests | ✗ | ✗ | ✗ | ✓ |

La navbar è ricostruita a ogni `rebuild()` come `LinkedHashMap<String, String>`: i pulsanti non autorizzati non vengono registrati e quindi non compaiono in `bar.getChildren()`. Il pattern adottato rispetta l'architettura esistente del file e produce un effetto equivalente a `setVisible(false)` + `setManaged(false)`.

---

## 3. Landing page post-login (default view)

Implementata in `game-client-emulator/.../infrastructure/ui/MainView.java:310-322` (`defaultViewAfterLogin()`).

Per evitare che un utente atterri su una pagina senza il pulsante navbar corrispondente, la destinazione è scelta in base al ruolo con priorità al più privilegiato:

| Ruolo (priorità alta → bassa) | Default view |
|---|---|
| PLATFORM_ADMIN | View Platform Admin |
| GAME_ADMIN | View Game Admin |
| LOCAL_ADMIN | View Local Dashboard |
| PLAYER (o fallback) | View Games |

Per utenti multi-ruolo (es. `PLAYER + LOCAL_ADMIN`), vince il ruolo più privilegiato nella tabella sopra.

---

## 4. Endpoint backend e policy `@PreAuthorize`

La visibilità UI è coerente con le policy `@PreAuthorize` lato backend (entrambi i moduli `central-system` e `local-server`). `PLATFORM_ADMIN` è considerato superuser e compare come `or hasRole('PLATFORM_ADMIN')` in ogni endpoint ruolo-specifico.

| Modulo | Endpoint | Ruolo richiesto (Spring Security) |
|---|---|---|
| local | `GET /api/games` | PLAYER or PLATFORM_ADMIN |
| local | `GET /api/players/me/statistics` | PLAYER or PLATFORM_ADMIN |
| local | `GET /api/players/tournaments/me/matches` | PLAYER or PLATFORM_ADMIN |
| local | `GET /api/players/me/matches/history` | PLAYER or PLATFORM_ADMIN |
| local | `POST /api/tournaments/{id}/participants` | PLAYER or PLATFORM_ADMIN |
| local | `/api/sessions/**` | PLAYER or PLATFORM_ADMIN |
| local | `/api/reservations/**` | PLAYER or PLATFORM_ADMIN (con self-check) |
| local | `/api/statistics` | PLAYER or PLATFORM_ADMIN |
| local | `/api/admin/local/**` (giochi, dispositivi, sessioni, statistiche building) | LOCAL_ADMIN or PLATFORM_ADMIN (con binding building check; PLATFORM_ADMIN bypassato) |
| local | `/api/admin/games/**` (game definitions) | GAME_ADMIN or PLATFORM_ADMIN |
| local | `/api/devices/register` | LOCAL_ADMIN or PLATFORM_ADMIN (con binding building check) |
| local | `/api/admin/users`, `/api/admin/servers`, `/api/admin/tournaments` | PLATFORM_ADMIN |
| central | `GET /api/players/me/statistics` | PLAYER or PLATFORM_ADMIN |
| central | `GET /api/players/{userId}/statistics` | self-check (owner or PLATFORM_ADMIN) |
| central | `GET /api/statistics` (aggregated globali) | PLATFORM_ADMIN |
| central | `POST /api/admin/games/definitions`, `PUT /api/admin/games/definitions/{gameType}` | GAME_ADMIN or PLATFORM_ADMIN |
| central | `/api/admin/tournaments/**` (create, open, schedule, cancel, update, delete) | PLATFORM_ADMIN |
| central | `/api/admin/local/buildings` (binding LOCAL_ADMIN↔building) | PLATFORM_ADMIN |

---

## 5. Note di sicurezza

- Il backend resta la fonte di verità: anche se un utente manipolasse il client per mostrare pulsanti nascosti, gli endpoint REST restituiscono 403 se il JWT non ha il ruolo richiesto.
- `LocalAdminBuildingAuthorizationManager.canManageBuilding(...)` (local) bypassa PLATFORM_ADMIN (ritorna `true` se il principal ha `ROLE_PLATFORM_ADMIN`); per LOCAL_ADMIN reale richiede la riga `(userId, buildingId)` in `local_admin_buildings_local`.
- JWT non viene ruotato dopo un cambio ruolo: il vecchio token resta valido fino alla scadenza (24h central, 1h local). Mitigato da `RolePreCheck` su `replicated_users` per le scritture admin lato local.
