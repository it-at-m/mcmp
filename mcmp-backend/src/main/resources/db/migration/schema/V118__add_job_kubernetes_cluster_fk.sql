SET client_encoding = 'UTF8';

ALTER TABLE cmp.job
    ADD COLUMN kubernetes_cluster_id BIGINT;

ALTER TABLE cmp.job
    ADD CONSTRAINT fk_kubernetes_cluster_id
        FOREIGN KEY (kubernetes_cluster_id)
            REFERENCES cmp.kubernetes_cluster (id)
            ON DELETE SET NULL;

CREATE INDEX idx_job_kubernetes_cluster_id ON cmp.job (kubernetes_cluster_id);
