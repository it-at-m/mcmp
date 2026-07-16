SET client_encoding = 'UTF8';

CREATE TABLE cmp.repository
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version    BIGINT                   NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ                       DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMPTZ                       DEFAULT CURRENT_TIMESTAMP(3),
    name       TEXT                     NOT NULL UNIQUE,
    snow_name TEXT,
    snow_sys_id TEXT,
    snow_sys_class TEXT,
    snow_last_discovered timestamp with time zone,
    CONSTRAINT uq_repository_name UNIQUE (name)

);
ALTER TABLE cmp.repository OWNER TO cmp;
CREATE INDEX idx_repository_name_trgm ON cmp.repository USING gin (name cmp.gin_trgm_ops);

CREATE TABLE cmp.repository_assignment
(
    repository_id BIGINT NOT NULL,
    server_id     BIGINT NOT NULL,
    PRIMARY KEY (repository_id, server_id),
    CONSTRAINT fk_repo_assign_repository FOREIGN KEY (repository_id) REFERENCES cmp.repository (id) ON DELETE CASCADE,
    CONSTRAINT fk_repo_assign_server     FOREIGN KEY (server_id)     REFERENCES cmp.server (id)     ON DELETE CASCADE
);
ALTER TABLE cmp.repository_assignment OWNER TO cmp;
CREATE INDEX idx_repo_assign_server ON cmp.repository_assignment (server_id);

CREATE TABLE cmp.repository_has_appservices
(
    repository_id BIGINT NOT NULL,
    appservice_id BIGINT NOT NULL,
    PRIMARY KEY (repository_id, appservice_id),
    CONSTRAINT fk_repo_has_app_repository FOREIGN KEY (repository_id) REFERENCES cmp.repository (id) ON DELETE CASCADE,
    CONSTRAINT fk_repo_has_app_appservice FOREIGN KEY (appservice_id) REFERENCES cmp.appservice (id) ON DELETE CASCADE
);
ALTER TABLE cmp.repository_has_appservices OWNER TO cmp;
CREATE INDEX idx_repo_has_app_appservice ON cmp.repository_has_appservices (appservice_id);
