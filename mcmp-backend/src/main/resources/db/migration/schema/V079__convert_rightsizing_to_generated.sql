SET client_encoding = 'UTF8';

ALTER TABLE cmp.server DROP COLUMN IF EXISTS memory_mb_rightsizing;
ALTER TABLE cmp.server DROP COLUMN IF EXISTS num_cpu_rightsizing;

ALTER TABLE cmp.server
    ADD COLUMN memory_mb_rightsizing cmp.server_rightsizing_type GENERATED ALWAYS AS (
        CASE
            WHEN memory_mb_recommended = 0 OR memory_mb_recommended IS NULL THEN 'ok'::cmp.server_rightsizing_type
            WHEN memory_mb > memory_mb_recommended THEN 'oversized'::cmp.server_rightsizing_type
            WHEN memory_mb < memory_mb_recommended THEN 'undersized'::cmp.server_rightsizing_type
            ELSE 'ok'::cmp.server_rightsizing_type
            END
        ) STORED;

ALTER TABLE cmp.server
    ADD COLUMN num_cpu_rightsizing cmp.server_rightsizing_type GENERATED ALWAYS AS (
        CASE
            WHEN num_cpu_recommended = 0 OR num_cpu_recommended IS NULL THEN 'ok'::cmp.server_rightsizing_type
            WHEN num_cpu > num_cpu_recommended THEN 'oversized'::cmp.server_rightsizing_type
            WHEN num_cpu < num_cpu_recommended THEN 'undersized'::cmp.server_rightsizing_type
            ELSE 'ok'::cmp.server_rightsizing_type
            END
        ) STORED;