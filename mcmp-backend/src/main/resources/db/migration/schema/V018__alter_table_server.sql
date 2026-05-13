SET client_encoding = 'UTF8';
ALTER TABLE cmp.server DROP COLUMN "patchnight_group";
ALTER TABLE cmp.server ADD COLUMN "patchnight_included" BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE cmp.server ADD COLUMN "patchnight_environment" cmp.environment_type DEFAULT NULL;
ALTER TABLE cmp.server ADD COLUMN "patchnight_group" VARCHAR(10) DEFAULT null;
ALTER TABLE cmp.server ADD COLUMN "patchnight_start_date" TIMESTAMPTZ DEFAULT NULL;
ALTER TABLE cmp.server ADD COLUMN "patchnight_end_date" TIMESTAMPTZ DEFAULT NULL;