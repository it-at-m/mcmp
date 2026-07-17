SET client_encoding = 'UTF8';

CREATE TABLE cmp.user_favorite_appservice
(
    user_id       BIGINT NOT NULL CONSTRAINT fk_favorite_appservice_user REFERENCES cmp."user" (id) ON DELETE CASCADE,
    appservice_id BIGINT NOT NULL CONSTRAINT fk_favorite_appservice_appservice REFERENCES cmp.appservice (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, appservice_id)
);
ALTER TABLE cmp.user_favorite_appservice OWNER TO cmp;
CREATE INDEX idx_favorite_appservice_user_id ON cmp.user_favorite_appservice (user_id);

CREATE TABLE cmp.user_favorite_lb_virtual_server
(
    user_id              BIGINT NOT NULL CONSTRAINT fk_favorite_lb_user REFERENCES cmp."user" (id) ON DELETE CASCADE,
    lb_virtual_server_id BIGINT NOT NULL CONSTRAINT fk_favorite_lb_vs REFERENCES cmp.lb_virtual_server (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, lb_virtual_server_id)
);
ALTER TABLE cmp.user_favorite_lb_virtual_server OWNER TO cmp;
CREATE INDEX idx_favorite_lb_user_id ON cmp.user_favorite_lb_virtual_server (user_id);

-- Storage is unified across several backing entity types (Ontap volumes, qtrees, StorageGrid
-- buckets) with no shared numeric id space, so favorites are keyed by (type, uuid) instead of
-- a FK to a single table.
CREATE TABLE cmp.user_favorite_storage
(
    user_id      BIGINT      NOT NULL CONSTRAINT fk_favorite_storage_user REFERENCES cmp."user" (id) ON DELETE CASCADE,
    storage_type VARCHAR(20) NOT NULL,
    storage_uuid VARCHAR(64) NOT NULL,
    PRIMARY KEY (user_id, storage_type, storage_uuid)
);
ALTER TABLE cmp.user_favorite_storage OWNER TO cmp;
CREATE INDEX idx_favorite_storage_user_id ON cmp.user_favorite_storage (user_id);
