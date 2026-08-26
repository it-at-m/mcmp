SET client_encoding = 'UTF8';

ALTER TABLE cmp.job
    ADD COLUMN lb_virtual_server_id BIGINT,
    ADD COLUMN ontap_volume_id BIGINT,
    ADD COLUMN ontap_qtree_id BIGINT,
    ADD COLUMN storagegrid_bucket_id BIGINT,
    ADD COLUMN kubernetes_namespace_id BIGINT;

ALTER TABLE cmp.job
    ADD CONSTRAINT fk_lb_virtual_server_id
        FOREIGN KEY (lb_virtual_server_id)
            REFERENCES cmp.lb_virtual_server (id)
            ON DELETE SET NULL,
    ADD CONSTRAINT fk_ontap_volume_id
        FOREIGN KEY (ontap_volume_id)
            REFERENCES cmp.ontap_volume (id)
            ON DELETE SET NULL,
    ADD CONSTRAINT fk_ontap_qtree_id
        FOREIGN KEY (ontap_qtree_id)
            REFERENCES cmp.ontap_qtree (id)
            ON DELETE SET NULL,
    ADD CONSTRAINT fk_storagegrid_bucket_id
        FOREIGN KEY (storagegrid_bucket_id)
            REFERENCES cmp.storagegrid_buckets (id)
            ON DELETE SET NULL,
    ADD CONSTRAINT fk_kubernetes_namespace_id
        FOREIGN KEY (kubernetes_namespace_id)
            REFERENCES cmp.kubernetes_namespace (id)
            ON DELETE SET NULL;

CREATE INDEX idx_job_lb_virtual_server_id ON cmp.job (lb_virtual_server_id);
CREATE INDEX idx_job_ontap_volume_id ON cmp.job (ontap_volume_id);
CREATE INDEX idx_job_ontap_qtree_id ON cmp.job (ontap_qtree_id);
CREATE INDEX idx_job_storagegrid_bucket_id ON cmp.job (storagegrid_bucket_id);
CREATE INDEX idx_job_kubernetes_namespace_id ON cmp.job (kubernetes_namespace_id);
