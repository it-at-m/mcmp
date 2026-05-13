SET client_encoding = 'UTF8';

ALTER TABLE cmp.job ADD COLUMN "quickdiscovery_error" TEXT;
ALTER TABLE cmp.job ADD COLUMN "quickdiscovery_ci_sysid" VARCHAR(100);
ALTER TABLE cmp.job ADD COLUMN "quickdiscovery_ci_name" VARCHAR(100);
ALTER TABLE cmp.job ADD COLUMN "change_error" TEXT;

ALTER TYPE cmp.job_status ADD VALUE 'rejected';
