SET client_encoding = 'UTF8';

CREATE TABLE database_instance (
    id                   BIGSERIAL PRIMARY KEY,
    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    snow_name            TEXT,
    snow_sys_id          TEXT         NOT NULL UNIQUE,
    snow_sys_class       TEXT,
    snow_last_discovered TIMESTAMP(3),
    snow_version         TEXT
);

CREATE TABLE database_pdb_instance (
    id                   BIGSERIAL PRIMARY KEY,
    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    snow_name            TEXT,
    snow_sys_id          TEXT         NOT NULL UNIQUE,
    snow_sys_class       TEXT,
    snow_last_discovered TIMESTAMP(3),
    snow_pdb             TEXT,
    pdb_name             TEXT,
    pdb_host_name        TEXT,
    pdb_characterset     TEXT,
    pdb_startup_time     TIMESTAMP(3),
    pdb_database_type    TEXT,
    pdb_collected_at     TIMESTAMP(3)
);

CREATE TABLE database_pdb_user (
    id                       BIGSERIAL PRIMARY KEY,
    version                  BIGINT       NOT NULL DEFAULT 0,
    created_at               TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at               TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    database_pdb_instance_id BIGINT       NOT NULL REFERENCES database_pdb_instance (id) ON DELETE CASCADE,
    user_name                TEXT         NOT NULL,
    account_status           TEXT,
    last_login               TIMESTAMP(3),
    profile                  TEXT,
    tablespaces              TEXT,
    UNIQUE (database_pdb_instance_id, user_name)
);

CREATE TABLE database_pdb_tablespace (
    id                       BIGSERIAL PRIMARY KEY,
    version                  BIGINT       NOT NULL DEFAULT 0,
    created_at               TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at               TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    database_pdb_instance_id BIGINT       NOT NULL REFERENCES database_pdb_instance (id) ON DELETE CASCADE,
    tablespace_name          TEXT         NOT NULL,
    tablespace_type          TEXT,
    data_max_in_b            BIGINT,
    data_used_in_b           BIGINT,
    UNIQUE (database_pdb_instance_id, tablespace_name)
);

CREATE TABLE server_has_database_instances (
    server_id             BIGINT NOT NULL REFERENCES server (id) ON DELETE CASCADE,
    database_instance_id  BIGINT NOT NULL REFERENCES database_instance (id) ON DELETE CASCADE,
    PRIMARY KEY (server_id, database_instance_id)
);

CREATE TABLE database_instance_has_database_pdb_instances (
    database_instance_id   BIGINT NOT NULL REFERENCES database_instance (id) ON DELETE CASCADE,
    database_pdb_instance_id BIGINT NOT NULL REFERENCES database_pdb_instance (id) ON DELETE CASCADE,
    PRIMARY KEY (database_instance_id, database_pdb_instance_id)
);

CREATE TABLE database_instance_has_appservices (
    database_instance_id BIGINT NOT NULL REFERENCES database_instance (id) ON DELETE CASCADE,
    appservice_id        BIGINT NOT NULL REFERENCES appservice (id) ON DELETE CASCADE,
    PRIMARY KEY (database_instance_id, appservice_id)
);

CREATE TABLE database_pdb_instance_has_appservices (
    database_pdb_instance_id BIGINT NOT NULL REFERENCES database_pdb_instance (id) ON DELETE CASCADE,
    appservice_id            BIGINT NOT NULL REFERENCES appservice (id) ON DELETE CASCADE,
    PRIMARY KEY (database_pdb_instance_id, appservice_id)
);

CREATE INDEX idx_server_snow_server_sys_id
    ON cmp.server (snow_server_sys_id);

CREATE INDEX idx_server_has_db_instances_db_instance_id_server_id
    ON cmp.server_has_database_instances (database_instance_id, server_id);

CREATE INDEX idx_db_instance_has_appservices_appservice_id_db_instance_id
    ON cmp.database_instance_has_appservices (appservice_id, database_instance_id);

CREATE INDEX idx_db_pdb_user_pdb_instance_id_user_name
    ON cmp.database_pdb_user (database_pdb_instance_id, user_name);

CREATE INDEX idx_db_pdb_tablespace_pdb_instance_id_tablespace_name
    ON cmp.database_pdb_tablespace (database_pdb_instance_id, tablespace_name);