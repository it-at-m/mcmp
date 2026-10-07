SET client_encoding = 'UTF8';

ALTER TABLE cmp.repository
    ADD COLUMN lock_status TEXT NOT NULL DEFAULT 'LOCKED'
        CONSTRAINT chk_repository_lock_status CHECK (lock_status IN ('LOCKED', 'SELF_ONLY', 'OPEN'));

UPDATE cmp.repository
SET lock_status = CASE WHEN locked THEN 'LOCKED' ELSE 'OPEN' END;

ALTER TABLE cmp.repository
    DROP COLUMN locked;
