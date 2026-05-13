SET client_encoding = 'UTF8';

ALTER TABLE cmp.job ALTER COLUMN awx_template_id TYPE BIGINT;
ALTER TABLE cmp.job ALTER COLUMN awx_job_id TYPE BIGINT;
ALTER TABLE cmp.job ADD COLUMN  awx_job_name TEXT;
ALTER TABLE cmp.job ADD COLUMN  awx_job_status TEXT;
ALTER TABLE cmp.job ADD COLUMN  awx_job_failed BOOLEAN;
ALTER TABLE cmp.job ADD COLUMN  awx_job_return_completed BOOLEAN;
ALTER TABLE cmp.job ADD COLUMN  awx_job_return_message TEXT;
ALTER TABLE cmp.job ADD COLUMN  awx_job_return_data TEXT;
ALTER TABLE cmp.job ADD COLUMN  awx_job_org TEXT;
ALTER TABLE cmp.job ADD COLUMN  awx_job_error_message TEXT;
ALTER TABLE cmp.job ADD COLUMN  awx_template_link TEXT;
ALTER TABLE cmp.job ADD COLUMN  awx_template_name TEXT;
ALTER TABLE cmp.job_nodes ADD COLUMN job_extra_vars TEXT;
