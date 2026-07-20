SET client_encoding = 'UTF8';

CREATE TABLE kubernetes_cluster (
    id          BIGSERIAL PRIMARY KEY,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    name        TEXT,
    sys_id      TEXT         NOT NULL UNIQUE,
    sys_class   TEXT,
    last_discovered TIMESTAMP(3),
    k8s_uid     TEXT         UNIQUE,
    environment environment_type
);

CREATE TABLE kubernetes_namespace (
    id          BIGSERIAL PRIMARY KEY,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    cluster_id  BIGINT       NOT NULL REFERENCES kubernetes_cluster (id) ON DELETE CASCADE,
    name        TEXT         NOT NULL,
    sys_id      TEXT         NOT NULL UNIQUE,
    sys_class   TEXT,
    last_discovered TIMESTAMP(3),
    k8s_uid     TEXT         UNIQUE,
    environment environment_type
);

CREATE TABLE kubernetes_namespace_has_appservices (
    kubernetes_namespace_id BIGINT NOT NULL REFERENCES kubernetes_namespace (id) ON DELETE CASCADE,
    appservice_id           BIGINT NOT NULL REFERENCES appservice (id) ON DELETE CASCADE,
    PRIMARY KEY (kubernetes_namespace_id, appservice_id)
);

CREATE INDEX idx_kubernetes_namespace_cluster_id ON kubernetes_namespace (cluster_id);