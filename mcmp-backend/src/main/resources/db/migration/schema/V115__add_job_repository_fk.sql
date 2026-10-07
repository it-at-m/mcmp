SET client_encoding = 'UTF8';

ALTER TABLE cmp.job
    ADD COLUMN repository_id BIGINT;

ALTER TABLE cmp.job
    ADD CONSTRAINT fk_job_repository_id
        FOREIGN KEY (repository_id)
            REFERENCES cmp.repository (id)
            ON DELETE SET NULL;

CREATE INDEX idx_job_repository_id ON cmp.job (repository_id);
