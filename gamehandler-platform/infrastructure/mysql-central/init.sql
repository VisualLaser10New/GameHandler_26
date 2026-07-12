-- ============================================================
-- CENTRAL SYSTEM SCHEMA
-- Allineato alle entita JPA di central-system:
--   UserJpaEntity, AggregatedStatisticsJpaEntity,
--   ProcessedEventJpaEntity, RegisteredLocalServerJpaEntity,
--   OutboxEventJpaEntity, FailedLoginAttemptJpaEntity,
--   ReplicationProgressJpaEntity
-- ============================================================

-- =============== TABELLE COMUNI ===============

CREATE TABLE users (
    id              VARCHAR(36)  PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    email           VARCHAR(100) NOT NULL,
    roles           VARCHAR(1024) NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email)
);

-- game_catalog: tabella di riferimento condivisa
-- (non mappata da entita JPA nel central-system; mantenuta per compatibilita)
CREATE TABLE game_catalog (
    id          VARCHAR(36) PRIMARY KEY,
    game_type   VARCHAR(50) NOT NULL,
    name        VARCHAR(100) NOT NULL,
    building_id VARCHAR(50) NOT NULL,
    status      VARCHAR(255) NOT NULL DEFAULT 'AVAILABLE',
    INDEX idx_building (building_id),
    INDEX idx_type (game_type)
);

-- =============== TABELLE CENTRAL SYSTEM ===============

CREATE TABLE aggregated_statistics (
    id                     VARCHAR(36) PRIMARY KEY,
    building_id            VARCHAR(50) NOT NULL,
    game_type              VARCHAR(50) NOT NULL,
    period_start           DATE NOT NULL,
    period_end             DATE NOT NULL,
    total_sessions         INT NOT NULL,
    avg_duration_seconds   INT NOT NULL,
    total_reservations     INT NOT NULL,
    total_aborted_sessions INT NOT NULL,
    data                   TEXT,
    UNIQUE KEY uk_building_type_period (building_id, game_type, period_start)
);

CREATE TABLE processed_events (
    event_id     VARCHAR(36) PRIMARY KEY,
    processed_at DATETIME(6) NOT NULL
);

CREATE TABLE local_servers (
    building_id  VARCHAR(50) PRIMARY KEY,
    base_url     VARCHAR(255) NOT NULL,
    last_seen_at DATETIME(6) NOT NULL,
    is_active    BOOLEAN NOT NULL DEFAULT TRUE,
    INDEX idx_active (is_active)
);

CREATE TABLE outbox_events (
    id          VARCHAR(36) PRIMARY KEY,
    event_type  VARCHAR(100) NOT NULL,
    payload     JSON NOT NULL,
    status      VARCHAR(20) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    sent_at     DATETIME(6),
    INDEX idx_outbox_status_created_at (status, created_at)
);

CREATE TABLE failed_login_attempts (
    id           VARCHAR(36) PRIMARY KEY,
    username     VARCHAR(50) NOT NULL,
    attempt_time DATETIME(6) NOT NULL,
    INDEX idx_failed_login_username_time (username, attempt_time)
);

CREATE TABLE replication_progress (
    id        VARCHAR(100) PRIMARY KEY,
    event_id  VARCHAR(36)  NOT NULL,
    server_id VARCHAR(50)  NOT NULL,
    UNIQUE KEY uk_replication_event_server (event_id, server_id)
);

-- =============== FASE 0 — Migrazione ruoli legacy ===============
-- Mappa i letterali legacy nei record preesistenti (no-op su DB vergini).
-- Si usano UPDATE exact-match per evitare il bug di REPLACE che trasformerebbe
-- "PLATFORM_ADMIN" in "PLATFORM_PLATFORM_ADMIN" (la stringa contiene "ADMIN").
UPDATE users SET roles = 'PLAYER'          WHERE roles = 'USER';
UPDATE users SET roles = 'PLATFORM_ADMIN'  WHERE roles = 'ADMIN';
UPDATE users SET roles = 'PLAYER,PLATFORM_ADMIN' WHERE roles = 'USER,ADMIN';
UPDATE users SET roles = 'PLAYER,PLATFORM_ADMIN' WHERE roles = 'ADMIN,USER';
UPDATE users SET roles = 'ROLE_PLAYER'     WHERE roles = 'ROLE_USER';
UPDATE users SET roles = 'ROLE_PLATFORM_ADMIN' WHERE roles = 'ROLE_ADMIN';

-- =============== FASE 1 — Local Admin ↔ Building binding ===============
-- Bind amministratore locale <-> edificio (Source of Truth centrale).
-- Replicato ai Local Server via outbox events LOCAL_ADMIN_BUILDING_ASSIGNED/REVOKED.
CREATE TABLE IF NOT EXISTS local_admin_buildings (
    user_id     VARCHAR(36)  NOT NULL,
    building_id VARCHAR(100) NOT NULL,
    assigned_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, building_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- =============== FASE 2 — Game Definitions (GAME_ADMIN) ===============
-- Definizioni di gioco configurabili (gestite da GAME_ADMIN). Source of truth
-- replicata ai Local via outbox GAME_DEFINITION_UPSERTED (vedi FASE 2 §1.5
-- del PIANO_UTENTI_TORNEI.md). PK su game_type (enum shared-domain).
CREATE TABLE IF NOT EXISTS game_definitions (
    game_type           VARCHAR(50)  NOT NULL,
    name                VARCHAR(200) NOT NULL,
    min_players         INT          NOT NULL DEFAULT 1,
    max_players         INT          NOT NULL DEFAULT 1,
    team_allowed        BOOLEAN      NOT NULL DEFAULT FALSE,
    registration_rules  JSON         NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (game_type)
) ENGINE=InnoDB;

-- Seed iniziale allineato al GameType enum esistente
INSERT INTO game_definitions (game_type, name, min_players, max_players, team_allowed) VALUES
  ('CHESS','Scacchi',2,2,FALSE),
  ('FOOSBALL','Calciobalilla',2,4,TRUE),
  ('DARTS','Freccette',1,4,TRUE),
  ('MONOPOLY','Monopoli',2,6,TRUE),
  ('RISK','Rischio',2,6,TRUE),
  ('SLOT_MACHINE','Slot Machine',1,1,FALSE),
  ('ROULETTE','Roulette',1,20,TRUE)
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- =============== FASE 3 — Statistiche del Giocatore ===============
-- Read-model per-giocatore popolato dal SyncEventProcessor consumando i
-- GAME_SESSION_COMPLETED arricchiti (PIANO_UTENTI_TORNEI.md §2). Nessun seed:
-- le tabelle si popolano a runtime. `tournament_id` resta NULL in FASE 3 (sarà
-- valorizzato in FASE 6 quando le sessioni saranno legate ai match di torneo).

-- Fatto per singola partita giocata da un utente
CREATE TABLE IF NOT EXISTS player_match_facts (
    session_id   VARCHAR(36)  NOT NULL,
    user_id      VARCHAR(36)  NOT NULL,
    building_id  VARCHAR(100) NOT NULL,
    game_type    VARCHAR(50)  NOT NULL,
    tournament_id VARCHAR(36) NULL,
    won          BOOLEAN      NOT NULL,
    win_condition VARCHAR(30) NULL,
    ended_at     TIMESTAMP    NOT NULL,
    PRIMARY KEY (session_id, user_id),
    INDEX idx_user (user_id, ended_at)
) ENGINE=InnoDB;

-- Proiezione aggregata per giocatore e tipo di gioco
CREATE TABLE IF NOT EXISTS player_statistics (
    user_id         VARCHAR(36) NOT NULL,
    game_type       VARCHAR(50) NOT NULL,
    matches_played  INT NOT NULL DEFAULT 0,
    matches_won     INT NOT NULL DEFAULT 0,
    last_played_at  TIMESTAMP NULL,
    PRIMARY KEY (user_id, game_type)
) ENGINE=InnoDB;

-- =============== FASE 4 — Dominio Torneo (CRUD + registrazione) ===============
-- 7 tabelle centrali per il dominio Torneo (PIANO_UTENTI_TORNEI.md §3.3).
-- tournament_team_members e' join table (PK composita team_id,user_id) modellata
-- come entita' standalone (mirror di SessionParticipantJpaEntity) senza
-- @OneToMany (RNF-08). 6 porte di dominio, 7 JPA entities (C.2 Option B).

CREATE TABLE IF NOT EXISTS tournaments (
    id          VARCHAR(36)  PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    game_type   VARCHAR(50)  NOT NULL,
    team_based  BOOLEAN      NOT NULL DEFAULT FALSE,
    team_size   INT          NOT NULL DEFAULT 1,
    format      VARCHAR(30)  NOT NULL DEFAULT 'SINGLE_ELIMINATION',
    status      VARCHAR(30)  NOT NULL,
    starts_at   TIMESTAMP    NOT NULL,
    ends_at     TIMESTAMP    NULL,
    created_by  VARCHAR(36)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    FOREIGN KEY (game_type) REFERENCES game_definitions(game_type)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS tournament_buildings (
    tournament_id VARCHAR(36) NOT NULL,
    building_id   VARCHAR(100) NOT NULL,
    PRIMARY KEY (tournament_id, building_id),
    FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS tournament_teams (
    id           VARCHAR(36) PRIMARY KEY,
    tournament_id VARCHAR(36) NOT NULL,
    name         VARCHAR(200) NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    UNIQUE(tournament_id, name),
    FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS tournament_team_members (
    team_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (team_id, user_id),
    FOREIGN KEY (team_id) REFERENCES tournament_teams(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS tournament_participants (
    tournament_id VARCHAR(36) NOT NULL,
    participant_id VARCHAR(36) NOT NULL,
    is_team       BOOLEAN     NOT NULL,
    display_name  VARCHAR(200) NOT NULL,
    registered_at TIMESTAMP   NOT NULL,
    PRIMARY KEY (tournament_id, participant_id),
    FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS tournament_matches (
    id               VARCHAR(36) PRIMARY KEY,
    tournament_id    VARCHAR(36) NOT NULL,
    round            INT NOT NULL,
    bracket_position INT NOT NULL,
    participant_a    VARCHAR(36) NOT NULL,
    participant_b    VARCHAR(36) NULL,
    building_id      VARCHAR(100) NULL,
    game_id          VARCHAR(100) NULL,
    session_id       VARCHAR(36) NULL,
    winner           VARCHAR(36) NULL,
    status           VARCHAR(30) NOT NULL,
    scheduled_at     TIMESTAMP NULL,
    played_at        TIMESTAMP NULL,
    result_data      TEXT NULL,
    FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS tournament_standings (
    tournament_id  VARCHAR(36) NOT NULL,
    participant_id VARCHAR(36) NOT NULL,
    wins           INT NOT NULL DEFAULT 0,
    losses         INT NOT NULL DEFAULT 0,
    points         INT NOT NULL DEFAULT 0,
    rank           INT NULL,
    PRIMARY KEY (tournament_id, participant_id),
    FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE
) ENGINE=InnoDB;