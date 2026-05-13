SET client_encoding = 'UTF8';

ALTER TABLE server ADD COLUMN hot_plug_memory_limit BIGINT;
ALTER TABLE server ADD COLUMN hot_plug_memory_increment_size BIGINT;

CREATE TYPE email_status AS ENUM ('new', 'sent', 'skipped', 'failed');

ALTER TABLE job ADD COLUMN awx_start_date TIMESTAMPTZ;
ALTER TABLE job ADD COLUMN awx_end_date TIMESTAMPTZ;
ALTER TABLE job ADD COLUMN awx_duration INTERVAL GENERATED ALWAYS AS (awx_end_date - awx_start_date) STORED;

ALTER TABLE job ADD COLUMN job_end_date TIMESTAMPTZ;
ALTER TABLE job ADD COLUMN job_duration INTERVAL GENERATED ALWAYS AS (job_end_date - created_at) STORED;

ALTER TABLE job ADD COLUMN non_postgres BOOLEAN DEFAULT false NOT NULL;
ALTER TABLE job ADD COLUMN non_postgres_justification TEXT;
ALTER TABLE job ADD COLUMN non_postgres_email_status email_status NOT NULL DEFAULT 'new';
UPDATE job SET non_postgres_email_status = 'skipped';

