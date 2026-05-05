-- =============== TABELLE COMUNI ===============

CREATE TABLE users (
    id              VARCHAR(36) PRIMARY KEY,
    username        VARCHAR(100) UNIQUE NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    email           VARCHAR(255),
    roles           VARCHAR(255) DEFAULT 'USER',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE game_catalog (
    id          VARCHAR(36) PRIMARY KEY,
    game_type   VARCHAR(50) NOT NULL,
    name        VARCHAR(100) NOT NULL,
    building_id VARCHAR(36) NOT NULL,
    status      ENUM('AVAILABLE','RESERVED','IN_USE','MAINTENANCE') DEFAULT 'AVAILABLE',
    INDEX idx_building (building_id),
    INDEX idx_type (game_type)
);

-- =============== TABELLE CENTRAL SYSTEM ===============

CREATE TABLE aggregated_statistics (
    id            VARCHAR(36) PRIMARY KEY,
    building_id   VARCHAR(36) NOT NULL,
    game_type     VARCHAR(50) NOT NULL,
    period_start  DATE NOT NULL,
    period_end    DATE NOT NULL,
    total_sessions INT DEFAULT 0,
    avg_duration_s INT DEFAULT 0,
    total_reservations INT DEFAULT 0,
    data          JSON,
    UNIQUE KEY uk_building_type_period (building_id, game_type, period_start)
);

CREATE TABLE processed_events (
    event_id    VARCHAR(36) PRIMARY KEY,
    processed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE local_servers (
    id           VARCHAR(36) PRIMARY KEY,
    building_id  VARCHAR(36) UNIQUE NOT NULL,
    base_url     VARCHAR(255) NOT NULL,
    last_seen_at DATETIME,
    is_active    BOOLEAN DEFAULT TRUE,
    INDEX idx_active (is_active)
);
