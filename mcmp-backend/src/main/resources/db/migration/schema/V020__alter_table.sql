SET client_encoding = 'UTF8';

ALTER TABLE cmp.network_group ADD COLUMN "description" TEXT;

ALTER TABLE cmp.config_snow ADD COLUMN "api_client_auth_url" VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE cmp.config_snow ADD COLUMN "api_client_id" VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE cmp.config_snow ADD COLUMN "api_client_secret_encrypted" BYTEA NOT NULL DEFAULT '';

ALTER TABLE cmp.action ALTER COLUMN "awx_extra_vars" TYPE TEXT;
ALTER TABLE cmp.job ALTER COLUMN "awx_extra_vars" TYPE TEXT;

ALTER TABLE cmp.server ADD COLUMN "patchnight_exitcode" SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE cmp.server ADD COLUMN "patchnight_exitstring" VARCHAR(100);
ALTER TABLE cmp.server ADD COLUMN "patchnight_change_number" SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE cmp.server ADD COLUMN "patchnight_change_sys_id" VARCHAR(100);

ALTER TYPE cmp.job_status ADD VALUE 'approved' AFTER 'waiting_for_approval';
