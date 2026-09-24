CREATE INDEX IF NOT EXISTS idx_job_awx_job_id ON cmp.job (awx_job_id);
CREATE INDEX IF NOT EXISTS idx_job_nodes_job_awx_id ON cmp.job_nodes (job_awx_id);
