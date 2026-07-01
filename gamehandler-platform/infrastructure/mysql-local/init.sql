-- =============== TABELLE COMUNI ===============

CREATE TABLE users (
    id              VARCHAR(36) PRIMARY KEY,
    username        VARCHAR(100) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    email           VARCHAR(255),
    roles           VARCHAR(255) DEFAULT 'USER',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
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

-- =============== TABELLE LOCAL SERVER ===============

CREATE TABLE reservations (
    id          VARCHAR(36) PRIMARY KEY,
    game_id     VARCHAR(36) NOT NULL,
    user_id     VARCHAR(36) NOT NULL,
    status      VARCHAR(20) NOT NULL,
    start_time  DATETIME NOT NULL,
    end_time    DATETIME,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
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
    status        VARCHAR(30) NOT NULL,
    started_at    DATETIME NOT NULL,
    ended_at      DATETIME,
    duration_s    INT,
    winner_id     VARCHAR(36),
    win_condition VARCHAR(30),
    result_data   JSON,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_game_type (game_type),
    INDEX idx_building (building_id),
    INDEX idx_status (status),
    INDEX idx_winner (winner_id)
);

CREATE TABLE session_participants (
    session_id  VARCHAR(36) NOT NULL,
    user_id     VARCHAR(36) NOT NULL,
    role        VARCHAR(30),
    joined_at   DATETIME,
    PRIMARY KEY (session_id, user_id),
    INDEX idx_user (user_id)
);

CREATE TABLE outbox_events (
    id          VARCHAR(36) PRIMARY KEY,
    event_type  VARCHAR(50) NOT NULL,
    payload     JSON NOT NULL,
    status      VARCHAR(20) DEFAULT 'PENDING',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at     DATETIME,
    retry_count INT DEFAULT 0,
    INDEX idx_status (status)
);

CREATE TABLE replicated_users (
    user_id       VARCHAR(36) PRIMARY KEY,
    username      VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    roles         VARCHAR(255),
    synced_at     DATETIME NOT NULL
);

CREATE TABLE local_statistics_cache (
    id              VARCHAR(36) PRIMARY KEY,
    game_type       VARCHAR(50) NOT NULL,
    period_date     DATE NOT NULL,
    data            JSON,
    computed_at     DATETIME NOT NULL,
    UNIQUE KEY      uk_type_period (game_type, period_date)
);
