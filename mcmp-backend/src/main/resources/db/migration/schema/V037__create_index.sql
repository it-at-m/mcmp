SET client_encoding = 'UTF8';

CREATE INDEX idx_job_active_only ON job (hostname) WHERE status NOT IN ('successful', 'failed', 'error', 'canceled') AND server_installation = true;

ALTER TYPE cmp.environment_type RENAME VALUE 'TL' TO 'T';
