SET client_encoding = 'UTF8';

DROP TABLE IF EXISTS cmp.ontap_volume_has_servers;
DROP TABLE IF EXISTS cmp.ontap_qtree_has_servers;

CREATE TABLE cmp.ontap_volume_server_mount (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    mount_point TEXT,
    filesystem TEXT,
    options TEXT[],
    ontap_volume_id BIGINT NOT NULL REFERENCES ontap_volume(id) ON DELETE CASCADE,
    server_id BIGINT NOT NULL REFERENCES server(id) ON DELETE CASCADE,
    CONSTRAINT uq_ontap_volume_server_mount UNIQUE (ontap_volume_id, server_id, mount_point)
);
ALTER TABLE cmp.ontap_volume_server_mount OWNER TO cmp;
CREATE INDEX IF NOT EXISTS ix_ontap_volume_server_mount_server ON cmp.ontap_volume_server_mount(server_id);
CREATE INDEX IF NOT EXISTS ix_ontap_volume_server_mount_volume ON cmp.ontap_volume_server_mount(ontap_volume_id);

CREATE TABLE cmp.ontap_qtree_server_mount (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    mount_point TEXT,
    filesystem TEXT,
    options TEXT[],
    ontap_qtree_id BIGINT NOT NULL REFERENCES ontap_qtree(id) ON DELETE CASCADE,
    server_id BIGINT NOT NULL REFERENCES server(id) ON DELETE CASCADE,
    CONSTRAINT uq_ontap_qtree_server_mount UNIQUE (ontap_qtree_id, server_id, mount_point)
);
ALTER TABLE cmp.ontap_qtree_server_mount OWNER TO cmp;
CREATE INDEX IF NOT EXISTS ix_ontap_qtree_server_mount_server ON cmp.ontap_qtree_server_mount(server_id);
CREATE INDEX IF NOT EXISTS ix_ontap_qtree_server_mount_qtree ON cmp.ontap_qtree_server_mount(ontap_qtree_id);

CREATE INDEX idx_ontap_qtree_mount_path_nfs ON cmp.ontap_qtree (mount_path_nfs);
CREATE INDEX idx_ontap_volume_mount_path_nfs ON cmp.ontap_volume (mount_path_nfs);