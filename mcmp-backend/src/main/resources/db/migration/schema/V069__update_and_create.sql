SET client_encoding = 'UTF8';

ALTER TABLE cmp.storagegrid_buckets ADD COLUMN quotaObjectBytes BIGINT;
ALTER TABLE cmp.storagegrid_buckets ADD COLUMN region TEXT;


CREATE TABLE cmp.server_metrics (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    created_at       timestamptz(0) NOT NULL DEFAULT date_trunc('minute', CURRENT_TIMESTAMP),
    server_id        BIGINT NOT NULL REFERENCES cmp.server(id) ON DELETE CASCADE,
    cpu_util         REAL,
    mem_used_percent REAL,
    UNIQUE (server_id, created_at)
) PARTITION BY RANGE (created_at);

DO $$
DECLARE
    i int;
    d date;
    partition_name text;
BEGIN
    FOR i IN 0..3 LOOP
            d := CURRENT_DATE + i;
            partition_name := 'server_metrics_' || to_char(d, 'YYYY_MM_DD');
            EXECUTE format('CREATE TABLE IF NOT EXISTS cmp.%I PARTITION OF cmp.server_metrics FOR VALUES FROM (%L) TO (%L)', partition_name, d, d + 1);
        END LOOP;
END $$;