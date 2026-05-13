SET client_encoding = 'UTF8';

DROP TABLE IF EXISTS app_config;
CREATE TABLE app_config (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    config_key TEXT UNIQUE NOT NULL ,
    config_value TEXT NOT NULL,
    updated_by TEXT NOT NULL
);
ALTER TABLE app_config OWNER TO cmp;
INSERT INTO app_config (config_key, config_value, updated_by) VALUES ('SYSTEM_MODE', 'NORMAL', 'SYSTEM');
INSERT INTO app_config (config_key, config_value, updated_by) VALUES ('MAINTENANCE_MESSAGE', 'Die Anwendung ist derzeit aufgrund von Wartungsarbeiten nur eingeschränkt verfügbar.', 'SYSTEM');


DROP TABLE IF EXISTS health_status;
CREATE TABLE health_status (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    identifier TEXT UNIQUE NOT NULL,
    display_name TEXT NOT NULL,
    description TEXT,
    expected_interval_minutes INTEGER NOT NULL DEFAULT 60,
    quiet_period_start TIME,
    quiet_period_end TIME,
    failure_threshold_yellow INTEGER NOT NULL DEFAULT 3,
    failure_threshold_red INTEGER NOT NULL DEFAULT 10,
    last_success_at timestamptz,
    consecutive_failures INTEGER DEFAULT 0 NOT NULL,
    error_message TEXT
);
ALTER TABLE health_status OWNER TO cmp;
