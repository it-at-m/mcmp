SET client_encoding = 'UTF8';

CREATE TABLE cmp.job_nodes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    job_id BIGINT NOT NULL REFERENCES cmp.job(id) ON DELETE CASCADE,
    node_id BIGINT NOT NULL,
    node_alias TEXT,
    node_identifier TEXT,
    parent_job_id BIGINT NOT NULL,
    parent_job_link TEXT,
    template_id BIGINT,
    template_link TEXT,
    template_name TEXT,
    template_type TEXT,
    job_awx_id BIGINT,
    job_awx_link TEXT,
    job_name TEXT,
    job_type TEXT,
    job_status TEXT,
    job_failed BOOLEAN,
    job_return_completed BOOLEAN,
    job_return_message TEXT,
    job_return_data TEXT,
    job_org TEXT,
    job_started timestamptz,
    job_finished timestamptz,
    job_duration INTERVAL GENERATED ALWAYS AS (job_finished - job_started) STORED,
    job_depth INTEGER,
    job_error_message TEXT
);
ALTER TABLE cmp.job_nodes OWNER TO cmp;
CREATE INDEX idx_job_nodes_job_id_id ON cmp.job_nodes (job_id, id);
