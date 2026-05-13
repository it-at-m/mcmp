SET client_encoding = 'UTF8';

ALTER TYPE cmp.job_status ADD VALUE 'quickdiscovery_failed';
ALTER TYPE cmp.job_status ADD VALUE 'tagging_failed';

ALTER TABLE cmp.server ADD COLUMN patchnight_exitcode_change_date timestamp with time zone default CURRENT_TIMESTAMP(3);
