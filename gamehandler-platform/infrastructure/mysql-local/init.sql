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
    INDEX idx_game_type (game_type),
    INDEX idx_building (building_id),
    INDEX idx_status (status),
    INDEX idx_winner (winner_id)
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