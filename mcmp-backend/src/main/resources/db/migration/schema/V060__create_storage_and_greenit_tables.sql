SET client_encoding = 'UTF8';

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS citext;

DROP TABLE IF EXISTS cmp.config_storage CASCADE;

ALTER TABLE cloud ADD COLUMN vcenter_short_code CITEXT CHECK (length(vcenter_short_code) <= 1);
CREATE INDEX idx_cloud_vcenter_short_code_cloud_type ON cloud (cloud_type, vcenter_short_code);

UPDATE cloud SET vcenter_short_code = 'c' WHERE name ilike 'vcenterc%';
UPDATE cloud SET vcenter_short_code = 'k' WHERE name ilike 'vcenterk%';
UPDATE cloud SET vcenter_short_code = 'p' WHERE name ilike 'vcenterp%';

CREATE TABLE green_it_rightsizing (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    vm_name TEXT NOT NULL,
    start_time TIMESTAMPTZ  NOT NULL,
    cpu_current INTEGER NOT NULL CHECK (cpu_current > 0),
    cpu_new INTEGER NOT NULL CHECK (cpu_new > 0),
    ram_current INTEGER NOT NULL CHECK (ram_current > 0),
    ram_new INTEGER NOT NULL CHECK (ram_new > 0),
    server_uuid TEXT NOT NULL,
    vcenter_short_code CITEXT NOT NULL CHECK (length(vcenter_short_code) <= 1),
    server_id BIGINT REFERENCES server(id) ON DELETE SET NULL,
    appservice_id BIGINT REFERENCES appservice("id") ON DELETE SET NULL,
    job_id BIGINT REFERENCES job(id) ON DELETE SET NULL
);
ALTER TABLE green_it_rightsizing OWNER TO cmp;

CREATE TABLE green_it_power_off (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    vm_name TEXT NOT NULL,
    cpu_current INTEGER NOT NULL CHECK (cpu_current > 0),
    ram_current INTEGER NOT NULL CHECK (ram_current > 0),
    start_time TIMESTAMPTZ NOT NULL,
    server_uuid TEXT NOT NULL,
    vcenter_short_code CITEXT NOT NULL CHECK (length(vcenter_short_code) <= 1),
    server_id BIGINT REFERENCES server(id) ON DELETE SET NULL,
    appservice_id BIGINT REFERENCES appservice("id") ON DELETE SET NULL,
    job_id BIGINT REFERENCES job(id) ON DELETE SET NULL
);
ALTER TABLE green_it_power_off OWNER TO cmp;

CREATE TABLE config_ontap_cluster (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    api_description TEXT,
    api_username TEXT,
    api_password_encrypted BYTEA,
    api_endpoint TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    datacenter TEXT,
    CONSTRAINT unique_config_ontap_cluster_api_endpoint UNIQUE (api_endpoint)
);
ALTER TABLE config_ontap_cluster OWNER TO cmp;

CREATE TABLE ontap_svm (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    ontap_cluster_id BIGINT NOT NULL REFERENCES config_ontap_cluster(id) ON DELETE CASCADE,
    swm_uuid UUID NOT NULL,
    name TEXT NOT NULL,
    UNIQUE (ontap_cluster_id, swm_uuid)
);
ALTER TABLE ontap_svm OWNER TO cmp;
CREATE INDEX idx_svm_cluster_uuid ON ontap_svm(ontap_cluster_id, swm_uuid);

CREATE TABLE ontap_export_policy (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    ontap_cluster_id BIGINT NOT NULL REFERENCES config_ontap_cluster(id) ON DELETE CASCADE,
    export_policy_id BIGINT NOT NULL,
    name TEXT,
    UNIQUE (ontap_cluster_id, export_policy_id)
);
ALTER TABLE ontap_export_policy OWNER TO cmp;

CREATE TABLE ontap_volume (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    svm_id BIGINT NOT NULL REFERENCES ontap_svm(id) ON DELETE CASCADE,
    ontap_cluster_id BIGINT NOT NULL REFERENCES config_ontap_cluster(id) ON DELETE CASCADE,
    volume_uuid UUID NOT NULL,
    name TEXT NOT NULL,
    size BIGINT,
    state TEXT,
    type TEXT,
    style TEXT,
    snapshot_policy TEXT,
    nas_path TEXT,
    mount_path_nfs TEXT,
    is_flex_clone BOOLEAN DEFAULT FALSE,
    parent_volume_name TEXT,
    parent_volume_uuid UUID,
    parent_snapshot_name TEXT,
    parent_snapshot_uuid UUID,
    parent_svm_name TEXT,
    parent_svm_uuid UUID,
    is_split_initiated BOOLEAN DEFAULT FALSE,
    space_available_percent INTEGER,
    space_afs_total BIGINT,
    space_logical_used BIGINT,
    space_logical_available BIGINT,
    space_logical_used_percent INTEGER,
    space_logical_used_by_afs BIGINT,
    space_snapshot_reserve_percent INTEGER,
    space_snapshot_reserve_size BIGINT,
    space_snapshot_used BIGINT,
    snaplock_append_mode_enabled BOOLEAN,
    snaplock_autocommit_period TEXT,
    snaplock_retention_default TEXT,
    snaplock_retention_minimum TEXT,
    snaplock_retention_maximum TEXT,
    export_policy_id BIGINT REFERENCES ontap_export_policy(id),
    UNIQUE (ontap_cluster_id, volume_uuid)
);
ALTER TABLE ontap_volume OWNER TO cmp;
CREATE INDEX idx_volume_cluster_uuid ON ontap_volume(ontap_cluster_id, volume_uuid);
CREATE INDEX idx_volume_svm ON ontap_volume(svm_id);
CREATE INDEX idx_volume_export_policy ON ontap_volume(export_policy_id);

CREATE TABLE ontap_volume_has_servers (
    ontap_volume_id BIGINT NOT NULL REFERENCES ontap_volume(id) ON DELETE CASCADE,
    server_id BIGINT NOT NULL REFERENCES server(id) ON DELETE CASCADE,
    PRIMARY KEY (ontap_volume_id, server_id)
);
ALTER TABLE ontap_volume_has_servers OWNER TO cmp;

CREATE TABLE ontap_volume_has_appservices (
    ontap_volume_id BIGINT NOT NULL REFERENCES ontap_volume(id) ON DELETE CASCADE,
    appservice_id BIGINT NOT NULL REFERENCES appservice("id") ON DELETE CASCADE,
    PRIMARY KEY (ontap_volume_id, appservice_id)
);
ALTER TABLE ontap_volume_has_appservices OWNER TO cmp;

CREATE TABLE ontap_export_policy_rule (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    policy_id BIGINT NOT NULL REFERENCES ontap_export_policy(id) ON DELETE CASCADE,
    clients TEXT[],
    protocols TEXT[],
    rw_rules TEXT[],
    ro_rules TEXT[]
);
ALTER TABLE ontap_export_policy_rule OWNER TO cmp;
CREATE INDEX idx_export_policy_ontap_cluster_id ON ontap_export_policy(ontap_cluster_id, export_policy_id);
CREATE INDEX idx_rule_policy ON ontap_export_policy_rule(policy_id);

CREATE TABLE ontap_cifs_share (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    volume_id BIGINT NOT NULL REFERENCES ontap_volume(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    path TEXT,
    mount_path_cifs TEXT
);
ALTER TABLE ontap_cifs_share OWNER TO cmp;
CREATE INDEX idx_share_volume ON ontap_cifs_share(volume_id);

CREATE TABLE ontap_cifs_share_has_appservices (
    ontap_cifs_share_id BIGINT NOT NULL REFERENCES ontap_cifs_share(id) ON DELETE CASCADE,
    appservice_id BIGINT NOT NULL REFERENCES appservice("id") ON DELETE CASCADE,
    PRIMARY KEY (ontap_cifs_share_id, appservice_id)
);
ALTER TABLE ontap_cifs_share_has_appservices OWNER TO cmp;

CREATE TABLE ontap_cifs_share_acl (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    share_id BIGINT NOT NULL REFERENCES ontap_cifs_share(id) ON DELETE CASCADE,
    user_or_group TEXT,
    permission TEXT
);
ALTER TABLE ontap_cifs_share_acl OWNER TO cmp;
CREATE INDEX idx_acl_share ON ontap_cifs_share_acl(share_id);

CREATE TABLE ontap_qtree (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    volume_id BIGINT NOT NULL REFERENCES ontap_volume(id) ON DELETE CASCADE,
    qtree_id BIGINT,
    name TEXT,
    path TEXT,
    mount_path_nfs TEXT,
    security_style TEXT,
    export_policy_id BIGINT REFERENCES ontap_export_policy(id),
    quota_index BIGINT,
    quota_type TEXT,
    quota_hard_limit BIGINT,
    quota_used_bytes BIGINT,
    quota_used_percent INTEGER
);
ALTER TABLE ontap_qtree OWNER TO cmp;
CREATE INDEX idx_qtree_volume ON ontap_qtree(volume_id);
CREATE INDEX idx_qtree_volume_id ON ontap_qtree(volume_id, qtree_id);

CREATE TABLE ontap_qtree_has_servers (
    ontap_qtree_id BIGINT NOT NULL REFERENCES ontap_qtree(id) ON DELETE CASCADE,
    server_id BIGINT NOT NULL REFERENCES server(id) ON DELETE CASCADE,
    PRIMARY KEY (ontap_qtree_id, server_id)
);
ALTER TABLE ontap_qtree_has_servers OWNER TO cmp;

CREATE TABLE ontap_qtree_has_appservices (
    ontap_qtree_id BIGINT NOT NULL REFERENCES ontap_qtree(id) ON DELETE CASCADE,
    appservice_id BIGINT NOT NULL REFERENCES appservice("id") ON DELETE CASCADE,
    PRIMARY KEY (ontap_qtree_id, appservice_id)
);
ALTER TABLE ontap_qtree_has_appservices OWNER TO cmp;

CREATE TABLE ontap_snapshot (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    volume_id BIGINT NOT NULL REFERENCES ontap_volume(id) ON DELETE CASCADE,
    snapshot_uuid UUID NOT NULL,
    name TEXT NOT NULL,
    create_time TIMESTAMPTZ,
    UNIQUE (volume_id, snapshot_uuid)
);
ALTER TABLE ontap_snapshot OWNER TO cmp;
CREATE INDEX idx_snapshot_volume_uuid ON ontap_snapshot(volume_id, snapshot_uuid);

CREATE TABLE config_storagegrid (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    api_description TEXT,
    api_username TEXT,
    api_password_encrypted BYTEA,
    api_endpoint TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    datacenter TEXT,
    CONSTRAINT unique_config_storagegrid_api_endpoint UNIQUE (api_endpoint)
);
ALTER TABLE config_storagegrid OWNER TO cmp;

CREATE TABLE storagegrid_accounts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    config_storagegrid_id BIGINT NOT NULL REFERENCES config_storagegrid(id) ON DELETE CASCADE,
    account_id TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    capabilities TEXT[],
    use_account_identity_source BOOLEAN DEFAULT FALSE,
    allow_platform_services BOOLEAN DEFAULT FALSE,
    allow_select_object_content BOOLEAN DEFAULT FALSE,
    allow_compliance_mode BOOLEAN DEFAULT FALSE,
    max_retention_days BIGINT,
    max_retention_years BIGINT,
    quota_object_bytes BIGINT,
    data_bytes BIGINT,
    object_count BIGINT,
    calculation_time TEXT
);
ALTER TABLE storagegrid_accounts OWNER TO cmp;

CREATE TABLE storagegrid_accounts_has_appservices (
    storagegrid_accounts_id BIGINT NOT NULL REFERENCES storagegrid_accounts(id) ON DELETE CASCADE,
    appservice_id BIGINT NOT NULL REFERENCES appservice("id") ON DELETE CASCADE,
    PRIMARY KEY (storagegrid_accounts_id, appservice_id)
);
ALTER TABLE storagegrid_accounts_has_appservices OWNER TO cmp;

CREATE TABLE storagegrid_buckets (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    storagegrid_account_id BIGINT NOT NULL REFERENCES storagegrid_accounts(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    object_count BIGINT,
    data_bytes BIGINT
);
ALTER TABLE storagegrid_buckets OWNER TO cmp;

CREATE TABLE storagegrid_account_sync_rules (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    storagegrid_account_id BIGINT NOT NULL REFERENCES storagegrid_accounts(id) ON DELETE CASCADE,
    rule_key VARCHAR(255) NOT NULL,
    rule_value TEXT
);
ALTER TABLE storagegrid_account_sync_rules OWNER TO cmp;

CREATE TABLE storagegrid_account_federations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    storagegrid_account_id BIGINT NOT NULL REFERENCES storagegrid_accounts(id) ON DELETE CASCADE,
    connection_data TEXT
);
ALTER TABLE storagegrid_account_federations OWNER TO cmp;

CREATE INDEX idx_sg_accounts_config_account_id ON storagegrid_accounts(config_storagegrid_id, account_id);
CREATE INDEX idx_sg_buckets_account_name ON storagegrid_buckets(storagegrid_account_id, name);
CREATE INDEX idx_sg_sync_rules_account_key ON storagegrid_account_sync_rules(storagegrid_account_id, rule_key);
CREATE INDEX idx_sg_federations_account_data ON storagegrid_account_federations(storagegrid_account_id);
