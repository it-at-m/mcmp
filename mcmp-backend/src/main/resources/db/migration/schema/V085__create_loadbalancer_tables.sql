SET client_encoding = 'UTF8';

-- Virtual Servers imported from the BIG-IP management API
CREATE TABLE cmp.lb_virtual_server (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version      BIGINT        NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ            DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   TIMESTAMPTZ            DEFAULT CURRENT_TIMESTAMP(3),
    name         VARCHAR(512)  NOT NULL UNIQUE,
    listen       VARCHAR(32)   NOT NULL,
    forward      VARCHAR(32)   NOT NULL,
    port         INTEGER       NOT NULL,
    persistence  VARCHAR(64)   NOT NULL,
    waf_enabled  BOOLEAN       NOT NULL DEFAULT FALSE,
    waf_status   VARCHAR(32),
    redirect80   BOOLEAN       NOT NULL DEFAULT FALSE,
    addresses    JSONB,
    irules       JSONB,
    pool_refs    JSONB
);
ALTER TABLE cmp.lb_virtual_server OWNER TO cmp;

-- Pool configurations imported from the BIG-IP management API
CREATE TABLE cmp.lb_pool (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version           BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ            DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        TIMESTAMPTZ            DEFAULT CURRENT_TIMESTAMP(3),
    name              VARCHAR(512)  NOT NULL UNIQUE,
    lb_method         VARCHAR(64)   NOT NULL,
    monitor_condition VARCHAR(32),
    monitors          JSONB
);
ALTER TABLE cmp.lb_pool OWNER TO cmp;

-- Pool members belonging to a pool
CREATE TABLE cmp.lb_pool_member (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version           BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ            DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        TIMESTAMPTZ            DEFAULT CURRENT_TIMESTAMP(3),
    pool_id           BIGINT        NOT NULL REFERENCES cmp.lb_pool(id) ON DELETE CASCADE,
    ip                VARCHAR(45)   NOT NULL,
    port              INTEGER       NOT NULL,
    monitor_condition VARCHAR(32),
    monitors          JSONB
);
ALTER TABLE cmp.lb_pool_member OWNER TO cmp;
CREATE INDEX idx_lb_pool_member_pool_id ON cmp.lb_pool_member (pool_id);
