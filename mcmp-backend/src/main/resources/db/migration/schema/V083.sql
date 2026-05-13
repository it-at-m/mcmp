SET client_encoding = 'UTF8';

ALTER TABLE cmp.job_nodes ADD COLUMN job_is_root_cause BOOLEAN DEFAULT FALSE;
ALTER TABLE cmp.job_nodes ADD COLUMN job_artifacts TEXT;
ALTER TABLE cmp.job ADD COLUMN awx_job_artifacts TEXT;

