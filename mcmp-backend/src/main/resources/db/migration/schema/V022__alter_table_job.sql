SET client_encoding = 'UTF8';

ALTER TABLE cmp.job  ADD COLUMN "change_number" VARCHAR(15);
ALTER TABLE cmp.job  ADD COLUMN "change_sys_id" VARCHAR(100);
ALTER TABLE cmp.job  ADD COLUMN "change_link" VARCHAR(150);
ALTER TABLE cmp.job  ADD COLUMN "awx_job_id" INTEGER;
ALTER TABLE cmp.job  ADD COLUMN "awx_job_link" VARCHAR(150);
ALTER TABLE cmp.job  ADD COLUMN "awx_error" TEXT;
ALTER TYPE cmp.job_status ADD VALUE 'awx_completed' AFTER 'awx_running';
ALTER TYPE cmp.job_status ADD VALUE 'quickdiscovery_completed' AFTER 'waiting_for_quickdiscovery';
ALTER TYPE cmp.job_status ADD VALUE 'tagging_completed' AFTER 'waiting_for_tagging';
