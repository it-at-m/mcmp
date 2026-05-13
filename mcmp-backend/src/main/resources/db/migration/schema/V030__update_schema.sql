SET client_encoding = 'UTF8';

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_backup_server_id_save_time ON cmp.backup (server_id, save_time DESC);
CREATE INDEX idx_nic_tools_ip_address_gin ON cmp.nic USING gin (tools_ip_address gin_trgm_ops);
CREATE INDEX idx_server_assignment_appservice_id ON cmp.server_assignment(appservice_id);

ALTER TABLE cmp.server DROP COLUMN "forman_id";
ALTER TABLE cmp.server ADD COLUMN "foreman_id" bigint;
ALTER TABLE cmp.cmp.server ALTER COLUMN patchnight_exitcode drop not null;
ALTER TABLE cmp.cmp.server ALTER COLUMN patchnight_exitcode drop default;

CREATE TABLE cmp.config_baas (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  version BIGINT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
  api_description VARCHAR(100),
  api_endpoint varchar(500) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT FALSE,
  CONSTRAINT unique_config_baas_api_endpoint UNIQUE (api_endpoint)
);
ALTER TABLE cmp.config_baas OWNER TO cmp;

CREATE TYPE cmp.cloud_type AS ENUM ('VCENTER', 'PROXMOX', 'UCS_MANAGER', 'UCS_CIMC');

ALTER TABLE cmp.cloud ADD COLUMN "cloud_type" cmp.cloud_type;
ALTER TABLE cmp.cloud ADD COLUMN api_description VARCHAR(100);
ALTER TABLE cmp.cloud ADD COLUMN api_username VARCHAR(100);
ALTER TABLE cmp.cloud ADD COLUMN api_password_encrypted BYTEA;
ALTER TABLE cmp.cloud ADD COLUMN api_endpoint varchar(500);
ALTER TABLE cmp.cloud ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE cmp.cloud ADD COLUMN locked BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE cmp.cloud ADD COLUMN config_infoblox_id bigint;
ALTER TABLE cmp.cloud ADD COLUMN config_baas_id bigint;
ALTER TABLE cmp.cloud ADD CONSTRAINT fk_cloud_config_infoblox FOREIGN KEY (config_infoblox_id) REFERENCES cmp.config_infoblox(id) ON DELETE SET NULL;
ALTER TABLE cmp.cloud ADD CONSTRAINT fk_cloud_config_baas FOREIGN KEY (config_baas_id) REFERENCES cmp.config_baas(id) ON DELETE SET NULL;
ALTER TABLE cmp.cloud ADD CONSTRAINT unique_cloud_api_endpoint UNIQUE (api_endpoint);

CREATE TABLE cmp.cloud_unlocked_server (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  version BIGINT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
  cloud_id bigint NOT NULL,
  server_id bigint NOT NULL,
  CONSTRAINT fk_cloud_unlocked_server_cloud FOREIGN KEY (cloud_id) REFERENCES cmp.cloud(id) ON DELETE CASCADE,
  CONSTRAINT fk_cloud_unlocked_server_server FOREIGN KEY (server_id) REFERENCES cmp.server(id) ON DELETE CASCADE,
  CONSTRAINT cloud_unlocked_server_cloud_id_server_id_idx UNIQUE (cloud_id, server_id)
);
ALTER TABLE cmp.cloud_unlocked_server OWNER TO cmp;
CREATE INDEX idx_cloud_unlocked_server_cloud_id ON cmp.cloud_unlocked_server(cloud_id);
CREATE INDEX idx_cloud_unlocked_server_server_id ON cmp.cloud_unlocked_server(server_id);

CREATE TYPE cmp.storage_type AS ENUM ('ONTAP', 'STORAGE_GRID');

CREATE TABLE cmp.config_storage (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  version BIGINT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
  storage_type cmp.storage_type NOT NULL,
  api_description VARCHAR(100),
  api_username VARCHAR(100) NOT NULL,
  api_password_encrypted BYTEA NOT NULL,
  api_endpoint varchar(500) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT FALSE,
  CONSTRAINT unique_config_storage_api_endpoint UNIQUE (api_endpoint)
);
ALTER TABLE cmp.config_storage OWNER TO cmp;

CREATE TYPE cmp.privilege_type AS ENUM ('ROOT', 'ADMIN');

CREATE TABLE cmp.temporary_privileges (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  version BIGINT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
  user_id BIGINT NOT NULL,
  server_id BIGINT NOT NULL,
  privilege_type cmp.privilege_type NOT NULL,
  granted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  expires_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT fk_temporary_privileges_user FOREIGN KEY (user_id) REFERENCES cmp.user(id) ON DELETE CASCADE,
  CONSTRAINT fk_temporary_privileges_server FOREIGN KEY (server_id) REFERENCES cmp.server(id) ON DELETE CASCADE
);
ALTER TABLE cmp.temporary_privileges OWNER TO cmp;
CREATE INDEX idx_temporary_privileges_user_id ON cmp.temporary_privileges(user_id);
CREATE INDEX idx_temporary_privileges_server_id ON cmp.temporary_privileges(server_id);






