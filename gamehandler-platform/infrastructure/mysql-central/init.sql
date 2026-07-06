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