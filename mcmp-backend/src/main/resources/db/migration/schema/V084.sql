SET client_encoding = 'UTF8';

ALTER TYPE cmp.awx_status ADD VALUE 'waiting_for_incident_resolution';
ALTER TYPE cmp.awx_status ADD VALUE 'incident_successful';
ALTER TYPE cmp.awx_status ADD VALUE 'incident_failed';
ALTER TYPE cmp.awx_status ADD VALUE 'logical_failed';

ALTER TYPE cmp.change_status ADD VALUE 'waiting_for_incident_resolution';
ALTER TYPE cmp.change_status ADD VALUE 'incident_failed';

ALTER TYPE cmp.quickdiscovery_status ADD VALUE 'waiting_for_incident_resolution';
ALTER TYPE cmp.quickdiscovery_status ADD VALUE 'incident_failed';

ALTER TYPE cmp.tagging_status ADD VALUE 'waiting_for_incident_resolution';
ALTER TYPE cmp.tagging_status ADD VALUE 'incident_failed';

ALTER TYPE cmp.job_status ADD VALUE 'waiting_for_incident_resolution';
ALTER TYPE cmp.job_status ADD VALUE 'incident_failed';

CREATE TYPE cmp.incident_source_type AS ENUM ('awx', 'change', 'quickdiscovery', 'tagging');
CREATE TYPE cmp.incident_status AS ENUM ('open', 'resolved', 'failed');

ALTER TABLE cmp.action ADD COLUMN create_incidents BOOLEAN DEFAULT TRUE;

ALTER TABLE cmp.job ADD COLUMN create_incidents BOOLEAN DEFAULT TRUE;

CREATE TABLE cmp.job_incident (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    job_id BIGINT NOT NULL REFERENCES cmp.job(id) ON DELETE CASCADE,
    status cmp.incident_status DEFAULT 'open',
    source_type cmp.incident_source_type NOT NULL,
    short_description TEXT,
    description TEXT,
    caller_sys_id TEXT,
    cmdb_ci_sys_id TEXT,
    assignment_group_sys_id TEXT,
    assignment_group_name TEXT,
    change_sys_id TEXT,
    incident_sys_id TEXT NOT NULL,
    incident_number TEXT,
    incident_link TEXT,
    success BOOLEAN,
    error_message TEXT,
    close_code_label TEXT,
    close_code_value TEXT,
    resolved_at TIMESTAMPTZ,
    state_label TEXT,
    state_value TEXT,
    close_notes TEXT
);
ALTER TABLE cmp.job_incident OWNER TO cmp;
CREATE INDEX idx_job_incident_job_id ON cmp.job_incident (job_id);
CREATE INDEX idx_job_incident_sys_id ON cmp.job_incident (incident_sys_id);