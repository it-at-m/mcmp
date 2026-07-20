SET client_encoding = 'UTF8';

CREATE TABLE cmp.storagegrid_buckets_has_appservices (
    storagegrid_bucket_id BIGINT NOT NULL CONSTRAINT fk_sgbha_bucket     REFERENCES cmp.storagegrid_buckets (id) ON DELETE CASCADE,
    appservice_id         BIGINT NOT NULL CONSTRAINT fk_sgbha_appservice REFERENCES cmp.appservice (id)          ON DELETE CASCADE,
    PRIMARY KEY (storagegrid_bucket_id, appservice_id)
);
ALTER TABLE cmp.storagegrid_buckets_has_appservices OWNER TO cmp;
CREATE INDEX idx_sgbha_appservice ON cmp.storagegrid_buckets_has_appservices (appservice_id);
CREATE INDEX idx_sgaha_appservice ON cmp.storagegrid_accounts_has_appservices (appservice_id);


ALTER TABLE cmp.lb_virtual_server DROP COLUMN IF EXISTS snow_name;
ALTER TABLE cmp.lb_virtual_server DROP COLUMN IF EXISTS snow_sys_id;
ALTER TABLE cmp.lb_virtual_server DROP COLUMN IF EXISTS snow_sys_class;
ALTER TABLE cmp.lb_virtual_server DROP COLUMN IF EXISTS snow_last_discovered;


CREATE TABLE cmp.lb_virtual_server_ci
(
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version               BIGINT                   NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ                       DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            TIMESTAMPTZ                       DEFAULT CURRENT_TIMESTAMP(3),
    lb_virtual_server_id  BIGINT NOT NULL CONSTRAINT fk_lb_virtual_server_ci REFERENCES cmp.lb_virtual_server (id) ON DELETE CASCADE,
    snow_name             TEXT NOT NULL,
    snow_sys_id           TEXT NOT NULL UNIQUE,
    snow_sys_class        TEXT NOT NULL,
    snow_last_discovered  TIMESTAMP WITH TIME ZONE
);
ALTER TABLE cmp.lb_virtual_server_ci OWNER TO cmp;
CREATE INDEX idx_lb_snow_ci_lb_id ON cmp.lb_virtual_server_ci (lb_virtual_server_id);


CREATE TABLE cmp.lb_virtual_server_ci_has_appservices
(
    lb_virtual_server_ci_id BIGINT NOT NULL CONSTRAINT fk_lb_snow_ci REFERENCES cmp.lb_virtual_server_ci (id) ON DELETE CASCADE,
    appservice_id           BIGINT NOT NULL CONSTRAINT fk_appservice REFERENCES cmp.appservice (id) ON DELETE CASCADE,
    PRIMARY KEY (lb_virtual_server_ci_id, appservice_id)
);
ALTER TABLE cmp.lb_virtual_server_ci_has_appservices OWNER TO cmp;
CREATE INDEX idx_lb_snow_ci_appservice_ci ON cmp.lb_virtual_server_ci_has_appservices (lb_virtual_server_ci_id);
CREATE INDEX idx_lb_snow_ci_appservice_app ON cmp.lb_virtual_server_ci_has_appservices (appservice_id);