-- ============================================================
-- LOCAL SERVER SCHEMA
-- Allineato alle entita JPA di local-server:
--   LocalUserJpaEntity (users), GameJpaEntity (game_catalog),
--   ReservationJpaEntity, GameSessionJpaEntity,
--   SessionParticipantJpaEntity, OutboxEventJpaEntity,
--   DeadLetterEventJpaEntity, UserJpaEntity (replicated_users)
-- ============================================================

-- =============== TABELLE COMUNI ===============

CREATE TABLE users (
    id              VARCHAR(36) PRIMARY KEY,
    username        VARCHAR(100) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    email           VARCHAR(255),
    roles           VARCHAR(255) DEFAULT 'PLAYER',
    created_at      DATETIME(6) NOT NULL,
    CONSTRAINT uk_users_username UNIQUE (username)
);

CREATE TABLE game_catalog (
    id          VARCHAR(36) PRIMARY KEY,
    game_type   VARCHAR(50) NOT NULL,
    name        VARCHAR(100) NOT NULL,
    building_id VARCHAR(50) NOT NULL,
    status      ENUM('AVAILABLE','RESERVED','IN_USE','MAINTENANCE','LOBBY') NOT NULL DEFAULT 'AVAILABLE',
    version     BIGINT NOT NULL DEFAULT 0,
    INDEX idx_building (building_id),
    INDEX idx_type (game_type)
);

-- =============== TABELLE LOCAL SERVER ===============

CREATE TABLE reservations (
    id          VARCHAR(36) PRIMARY KEY,
    game_id     VARCHAR(36) NOT NULL,
    user_id     VARCHAR(36) NOT NULL,
    status      VARCHAR(20) NOT NULL,
    start_time  DATETIME(6) NOT NULL,
    end_time    DATETIME(6) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    version     BIGINT NOT NULL DEFAULT 0,
    INDEX idx_game (game_id),
    INDEX idx_user (user_id),
    INDEX idx_expiration (status, end_time),
    INDEX idx_availability (game_id, status, start_time, end_time)
);

CREATE TABLE game_sessions (
    id            VARCHAR(36) PRIMARY KEY,
    game_id       VARCHAR(36) NOT NULL,
    game_type     VARCHAR(50) NOT NULL,
    building_id   VARCHAR(36) NOT NULL,
    status        VARCHAR(20) NOT NULL,
    started_at    DATETIME(6) NOT NULL,
    ended_at      DATETIME(6),
    duration_s    INT,
    winner_id     VARCHAR(36),
    win_condition VARCHAR(30),
    result_data   JSON,
    version       BIGINT NOT NULL DEFAULT 0,
    tournament_match_id VARCHAR(36) NULL,
    tournament_id       VARCHAR(36) NULL,
    INDEX idx_game_type (game_type),
    INDEX idx_building (building_id),
    INDEX idx_status (status),
    INDEX idx_winner (winner_id),
    INDEX idx_game_sessions_tournament (tournament_match_id)
);

CREATE TABLE session_participants (
    session_id  VARCHAR(36) NOT NULL,
    user_id     VARCHAR(36) NOT NULL,
    PRIMARY KEY (session_id, user_id),
    INDEX idx_user (user_id)
);

CREATE TABLE outbox_events (
    id          VARCHAR(36) PRIMARY KEY,
    event_type  VARCHAR(50) NOT NULL,
    payload     JSON NOT NULL,
    status      VARCHAR(20) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    sent_at     DATETIME(6),
    retry_count INT NOT NULL,
    INDEX idx_outbox_status_created_at (status, created_at)
);

CREATE TABLE outbox_dead_letter (
    id              VARCHAR(36) PRIMARY KEY,
    event_id        VARCHAR(36) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSON NOT NULL,
    original_status VARCHAR(20) NOT NULL,
    retry_count     INT NOT NULL,
    reason          VARCHAR(255),
    promoted_at     DATETIME(6) NOT NULL,
    INDEX idx_dql_promoted_at (promoted_at)
);

CREATE TABLE replicated_users (
    user_id       VARCHAR(36) PRIMARY KEY,
    username      VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email         VARCHAR(100),
    roles         VARCHAR(255),
    synced_at     DATETIME(6) NOT NULL,
    event_time    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    version       BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_replicated_users_username UNIQUE (username)
);

CREATE TABLE local_statistics_cache (
    id              VARCHAR(36) PRIMARY KEY,
    game_type       VARCHAR(50) NOT NULL,
    period_date     DATE NOT NULL,
    data            JSON,
    computed_at     DATETIME(6) NOT NULL,
    UNIQUE KEY      uk_type_period (game_type, period_date)
);

-- Seed game catalog
INSERT INTO game_catalog (id, game_type, name, building_id, status) VALUES
('game-chess-1',   'CHESS',       'Chess Table 1',    'building-1', 'AVAILABLE'),
('game-foosball-1','FOOSBALL',    'Foosball Table 1', 'building-1', 'AVAILABLE'),
('game-darts-1',   'DARTS',       'Darts Board 1',    'building-1', 'AVAILABLE'),
('game-slot-1',    'SLOT_MACHINE','Slot Machine 1',   'building-1', 'AVAILABLE');

-- =============== FASE 0 — Migrazione ruoli legacy ===============
-- Mappa i letterali legacy nei record preesistenti (no-op su DB vergini).
-- UPDATE exact-match per evitare il bug di REPLACE su "PLATFORM_ADMIN" (contiene "ADMIN").
UPDATE users            SET roles = 'PLAYER'                  WHERE roles = 'USER';
UPDATE users            SET roles = 'PLATFORM_ADMIN'          WHERE roles = 'ADMIN';
UPDATE users            SET roles = 'PLAYER,PLATFORM_ADMIN'   WHERE roles = 'USER,ADMIN';
UPDATE users            SET roles = 'PLAYER,PLATFORM_ADMIN'   WHERE roles = 'ADMIN,USER';
UPDATE replicated_users SET roles = 'PLAYER'                  WHERE roles = 'USER';
UPDATE replicated_users SET roles = 'PLATFORM_ADMIN'          WHERE roles = 'ADMIN';
UPDATE replicated_users SET roles = 'PLAYER,PLATFORM_ADMIN'   WHERE roles = 'USER,ADMIN';
UPDATE replicated_users SET roles = 'PLAYER,PLATFORM_ADMIN'   WHERE roles = 'ADMIN,USER';
UPDATE replicated_users SET roles = 'ROLE_PLAYER'             WHERE roles = 'ROLE_USER';
UPDATE replicated_users SET roles = 'ROLE_PLATFORM_ADMIN'     WHERE roles = 'ROLE_ADMIN';

-- =============== FASE 1 — Replica binding LOCAL_ADMIN ↔ building ===============
-- Replica read-only dei binding admin/building replicati dal Central via outbox
-- (eventi LOCAL_ADMIN_BUILDING_ASSIGNED / _REVOKED). Usata da
-- LocalAdminBuildingAuthorizationManager per l'enforcement offline.
CREATE TABLE IF NOT EXISTS local_admin_buildings_local (
    user_id     VARCHAR(36)  NOT NULL,
    building_id VARCHAR(100) NOT NULL,
    assigned_at TIMESTAMP    NOT NULL,
    PRIMARY KEY (user_id, building_id)
) ENGINE=InnoDB;

-- =============== FASE 2 — Replica Game Definitions (read-only) ===============
-- Replica read-only delle definizioni di gioco replicati dal Central via outbox
-- GAME_DEFINITION_UPSERTED. Usata da GameSessionService.start (validazione
-- participants.size/teamBased) e da AdminLocalController POST /games (FASE 2).
CREATE TABLE IF NOT EXISTS game_definitions_local (
    game_type          VARCHAR(50)  NOT NULL,
    name               VARCHAR(200) NOT NULL,
    min_players        INT          NOT NULL,
    max_players        INT          NOT NULL,
    team_allowed       BOOLEAN      NOT NULL,
    registration_rules JSON         NULL,
    updated_at         TIMESTAMP    NOT NULL,
    PRIMARY KEY (game_type)
) ENGINE=InnoDB;

-- Seed game definitions locale (mirror del seed centrale in mysql-central/init.sql):
-- i seed centrali NON emettono outbox GAME_DEFINITION_UPSERTED, quindi senza
-- questo seed il LOCAL_ADMIN non potrebbe creare istanze dei game types non
-- ancora replicati (es. SLOT_MACHINE) -> POST /api/admin/local/games 400.
-- ON DUPLICATE KEY UPDATE garantisce idempotenza su DB gia popolati via replica.
INSERT INTO game_definitions_local (game_type, name, min_players, max_players, team_allowed, registration_rules, updated_at) VALUES
    ('CHESS',        'Scacchi',        2, 2,  FALSE, NULL, NOW()),
    ('FOOSBALL',     'Calciobalilla',  2, 4,  TRUE,  NULL, NOW()),
    ('DARTS',        'Freccette',      1, 4,  TRUE,  NULL, NOW()),
    ('MONOPOLY',     'Monopoli',       2, 6,  TRUE,  NULL, NOW()),
    ('RISK',         'Rischio',        2, 6,  TRUE,  NULL, NOW()),
    ('SLOT_MACHINE', 'Slot Machine',   1, 1,  FALSE, NULL, NOW()),
    ('ROULETTE',     'Roulette',       1, 20, TRUE,  NULL, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name), min_players=VALUES(min_players), max_players=VALUES(max_players), team_allowed=VALUES(team_allowed), updated_at=NOW();

-- =============== FASE 6 — Replica tournament matches (read-only) ===============
-- Replica read-only dei match del torneo destinati a questo building, replicati
-- dal Central via outbox TOURNAMENT_MATCH_SCHEDULED. Usata da
-- PlayerTournamentController (GET /me/matches, POST /matches/{id}/start) e da
-- GameSessionService.start (validazione status / participant / team_allowed).
-- Nessun buildingId: la tabella contiene SOLO i match instradati a questo
-- building (ambiguity O). Aggiornata solo dal sync; nessun @Version.
CREATE TABLE IF NOT EXISTS tournament_matches_local (
    id              VARCHAR(36) PRIMARY KEY,
    tournament_id   VARCHAR(36) NOT NULL,
    round           INT NOT NULL,
    bracket_position INT NOT NULL,
    participant_a   VARCHAR(36) NOT NULL,
    participant_b   VARCHAR(36) NULL,
    game_type       VARCHAR(50) NOT NULL,
    game_id         VARCHAR(100) NULL,
    status          VARCHAR(30) NOT NULL,
    scheduled_at    TIMESTAMP NULL,
    INDEX idx_tml_tournament (tournament_id),
    INDEX idx_tml_status (status)
) ENGINE=InnoDB;

-- =============== FASE 7-A2 — Replica tournament summaries (read-only) ===============
-- Replica read-only del sommario torneo (proiezione piatta dei tornei Central),
-- replicata via outbox TOURNAMENT_SUMMARY_UPSERTED (emesso da
-- TournamentService.create/open/cancel/update/delete). Populated only via push
-- Central->Local (≤5 min latenza). PK tournament_id; upsert idempotente.
-- buildingIds serializzato come JSON in TEXT (mapper gestisce la conversione,
-- mirror di registration_rules in game_definitions_local).
-- deleted DEFAULT FALSE: il sync service PHYSICALLY rimuove la riga su tombstone
-- (deleted=true), quindi ogni riga presente ha deleted=false.
-- Aggiornata solo dal sync; nessun @Version.
CREATE TABLE IF NOT EXISTS tournaments_summary_local (
    tournament_id       VARCHAR(36) PRIMARY KEY,
    name                VARCHAR(200) NOT NULL,
    game_type           VARCHAR(50) NOT NULL,
    team_based          BOOLEAN NOT NULL,
    team_size           INT NOT NULL,
    status              VARCHAR(50) NOT NULL,
    starts_at           TIMESTAMP NULL,
    ends_at             TIMESTAMP NULL,
    building_ids        TEXT NULL,
    participants_count  INT NOT NULL DEFAULT 0,
    deleted             BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at          TIMESTAMP NOT NULL,
    INDEX idx_tsl_status (status)
) ENGINE=InnoDB;

-- =============== FASE 7-B — Replica tournament standings (read-only) ===============
-- Replica read-only delle classifiche torneo (proiezione piana dei risultati Central),
-- replicata via outbox TOURNAMENT_STANDINGS_UPSERTED. PK composta (tournament_id,
-- participant_id); il sync service effettua delete+insert full-snapshot per tournamentId
-- (idempotente su re-delivery). Aggiornata solo dal sync; nessun @Version.
CREATE TABLE IF NOT EXISTS tournament_standings_local (
    tournament_id   VARCHAR(36) NOT NULL,
    participant_id VARCHAR(64) NOT NULL,
    display_name   VARCHAR(100) NOT NULL,
    wins           INT NOT NULL DEFAULT 0,
    losses         INT NOT NULL DEFAULT 0,
    points         INT NOT NULL DEFAULT 0,
    `rank`           INT NULL,
    updated_at     TIMESTAMP NOT NULL,
    PRIMARY KEY (tournament_id, participant_id),
    INDEX idx_tsl_tournament (tournament_id)
) ENGINE=InnoDB;

-- =============== FASE 7-B — Replica tournament participants (read-only) ===============
-- Replica read-only dei partecipanti torneo (proiezione piana degli iscritti Central),
-- replicata via outbox TOURNAMENT_PARTICIPANTS_UPSERTED. PK composta (tournament_id,
-- participant_id); il sync service effettua delete+insert full-snapshot per tournamentId.
CREATE TABLE IF NOT EXISTS tournament_participants_local (
    tournament_id   VARCHAR(36) NOT NULL,
    participant_id  VARCHAR(64) NOT NULL,
    is_team         BOOLEAN NOT NULL,
    display_name    VARCHAR(100) NOT NULL,
    registered_at   TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL,
    PRIMARY KEY (tournament_id, participant_id),
    INDEX idx_tpl_tournament (tournament_id)
) ENGINE=InnoDB;

-- =============== FASE 7-B — Replica registered local servers (read-only) ===============
-- Replica read-only del registry dei Local Server, replicata via outbox
-- LOCAL_SERVER_REGISTRY_UPSERTED. PK building_id; upsert idempotente. Permette al
-- PLATFORM_ADMIN di vedere il registry completo da qualsiasi Local (E1).
CREATE TABLE IF NOT EXISTS registered_local_servers_local (
    building_id   VARCHAR(64) PRIMARY KEY,
    base_url      VARCHAR(255) NOT NULL,
    last_seen_at  TIMESTAMP NULL,
    active        BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at    TIMESTAMP NOT NULL
) ENGINE=InnoDB;

-- =============== FASE 7-B — Admin requests (async write via outbox) ===============
-- Persistenza locale delle richieste async tramite outbox *_REQUESTED (W6/W9/W10/W12).
-- Scritta atomicamente con la riga OutboxEvent (requestId == eventId) dall'use case W.
-- Lifecycle: PENDING -> COMPLETED (chiamato dal *SyncService quando arriva l'evento di
-- ritorno Central con originatingRequestId) OPPURE PENDING -> FAILED (chiamato dal
-- AdminRequestTimeoutService a timeout). Indici (acting_user_id, status) per il
-- filtro AdminRequestsController.per-user e (status, created_at) per la query timeout.
CREATE TABLE IF NOT EXISTS admin_requests_local (
    request_id        VARCHAR(36) PRIMARY KEY,
    event_type        VARCHAR(64) NOT NULL,
    acting_user_id    VARCHAR(64) NOT NULL,
    acting_role       VARCHAR(32) NOT NULL,
    building_id       VARCHAR(64) NULL,
    payload           TEXT NOT NULL,
    status            VARCHAR(16) NOT NULL,
    result_data       TEXT NULL,
    created_at        TIMESTAMP NOT NULL,
    completed_at      TIMESTAMP NULL,
    outbox_event_id   VARCHAR(64) NULL,
    INDEX idx_arl_user_status (acting_user_id, status),
    INDEX idx_arl_status_created (status, created_at)
) ENGINE=InnoDB;

-- =============== BUG-TEAM-3 — team_members_local (Replica team→user membership) ===
-- Replica read-only della membership team→user del Central `tournament_team_members`,
-- replicata via outbox TEAM_MEMBERS_UPSERTED. PK composita (tournament_id, team_id,
-- user_id); il sync service effettua delete+insert full-snapshot per tournamentId.
-- Permette al PLAYER di vedere i match team_based in myMatches (JOIN con
-- tournament_matches_local su participant_a / participant_b = teamId ↔ user_id).
CREATE TABLE IF NOT EXISTS team_members_local (
    tournament_id VARCHAR(36) NOT NULL,
    team_id       VARCHAR(36) NOT NULL,
    user_id       VARCHAR(36) NOT NULL,
    PRIMARY KEY (tournament_id, team_id, user_id),
    INDEX idx_tml_user (user_id)
) ENGINE=InnoDB;