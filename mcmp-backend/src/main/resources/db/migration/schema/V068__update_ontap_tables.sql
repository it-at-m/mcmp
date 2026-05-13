SET client_encoding = 'UTF8';

ALTER TABLE cmp.ontap_export_policy_rule ADD COLUMN index BIGINT;

ALTER TABLE cmp.ontap_cifs_share ALTER COLUMN volume_id DROP NOT NULL;
ALTER TABLE cmp.ontap_cifs_share ADD COLUMN qtree_id BIGINT REFERENCES cmp.ontap_qtree(id) ON DELETE CASCADE;
ALTER TABLE cmp.ontap_cifs_share ADD CONSTRAINT check_volume_or_qtree CHECK (volume_id IS NOT NULL OR qtree_id IS NOT NULL);

CREATE TABLE cmp.ontap_aggregate (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    ontap_cluster_id BIGINT NOT NULL REFERENCES config_ontap_cluster(id) ON DELETE CASCADE,
    aggregate_uuid UUID NOT NULL,
    name TEXT NOT NULL,
    disk_class TEXT NOT NULL,
    mirror_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (ontap_cluster_id, aggregate_uuid)
);
ALTER TABLE cmp.ontap_aggregate OWNER TO cmp;
CREATE INDEX idx_aggregate_cluster_uuid ON ontap_aggregate(ontap_cluster_id, aggregate_uuid);

CREATE TABLE cmp.ontap_aggregate_has_volumes
(
    aggregate_id BIGINT NOT NULL CONSTRAINT fk_ontap_aggregate_id REFERENCES cmp.ontap_aggregate (id) ON DELETE CASCADE,
    volume_id  BIGINT NOT NULL CONSTRAINT fk_ontap_volume_id REFERENCES cmp.ontap_volume (id) ON DELETE CASCADE,
    PRIMARY KEY (aggregate_id, volume_id)
);
ALTER TABLE cmp.ontap_aggregate_has_volumes OWNER TO cmp;
CREATE INDEX idx_ontap_aggregate_volume_id ON cmp.ontap_aggregate_has_volumes (volume_id);

DROP TABLE IF EXISTS cmp.ontap_snapshot;

CREATE TABLE ontap_snapshot (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    ontap_cluster_id BIGINT NOT NULL REFERENCES config_ontap_cluster(id) ON DELETE CASCADE,
    snapshot_uuid UUID NOT NULL,
    name TEXT NOT NULL,
    create_time TIMESTAMPTZ,
    UNIQUE (ontap_cluster_id, snapshot_uuid)
);
ALTER TABLE ontap_snapshot OWNER TO cmp;
CREATE INDEX idx_snapshot_cluster_uuid ON ontap_snapshot(ontap_cluster_id, snapshot_uuid);

CREATE TABLE ontap_snapshot_has_volumes (
    ontap_snapshot_id BIGINT NOT NULL REFERENCES ontap_snapshot(id) ON DELETE CASCADE,
    ontap_volume_id BIGINT NOT NULL REFERENCES ontap_volume(id) ON DELETE CASCADE,
    PRIMARY KEY (ontap_snapshot_id, ontap_volume_id)
);
ALTER TABLE ontap_snapshot_has_volumes OWNER TO cmp;

ALTER TABLE cmp.ontap_volume DROP COLUMN parent_volume_name;
ALTER TABLE cmp.ontap_volume DROP COLUMN parent_volume_uuid;
ALTER TABLE cmp.ontap_volume DROP COLUMN parent_snapshot_name;
ALTER TABLE cmp.ontap_volume DROP COLUMN parent_snapshot_uuid;
ALTER TABLE cmp.ontap_volume DROP COLUMN parent_svm_name;
ALTER TABLE cmp.ontap_volume DROP COLUMN parent_svm_uuid;
ALTER TABLE cmp.ontap_volume ADD COLUMN parent_volume_id BIGINT REFERENCES cmp.ontap_volume(id) ON DELETE SET NULL ;
ALTER TABLE cmp.ontap_volume ADD COLUMN parent_snapshot_id BIGINT REFERENCES cmp.ontap_snapshot(id) ON DELETE SET NULL;
ALTER TABLE cmp.ontap_volume ADD COLUMN parent_svm_id BIGINT REFERENCES cmp.ontap_svm(id) ON DELETE SET NULL;

ALTER TABLE cmp.ontap_qtree DROP CONSTRAINT IF EXISTS ontap_qtree_export_policy_id_fkey;
ALTER TABLE cmp.ontap_qtree ADD CONSTRAINT ontap_qtree_export_policy_id_fkey FOREIGN KEY (export_policy_id) REFERENCES cmp.ontap_export_policy(id) ON DELETE SET NULL;

-- Drop and recreate constraint for ontap_volume
ALTER TABLE cmp.ontap_volume DROP CONSTRAINT IF EXISTS ontap_volume_export_policy_id_fkey;
ALTER TABLE cmp.ontap_volume ADD CONSTRAINT ontap_volume_export_policy_id_fkey FOREIGN KEY (export_policy_id) REFERENCES cmp.ontap_export_policy(id) ON DELETE SET NULL;

