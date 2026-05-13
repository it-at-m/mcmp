SET client_encoding = 'UTF8';
ALTER TABLE cmp.server DROP COLUMN IF EXISTS memory_usage_mb;
ALTER TABLE cmp.server DROP COLUMN IF EXISTS memory_usage_perc;
ALTER TABLE cmp.server DROP COLUMN IF EXISTS cpu_usage_perc;
ALTER TABLE cmp.server DROP COLUMN IF EXISTS cpu_usage;
ALTER TABLE cmp.server DROP COLUMN IF EXISTS cpu_max_usage;
