SET client_encoding = 'UTF8';
ALTER TABLE cmp.snapshot ADD COLUMN "retention_period" TIMESTAMPTZ DEFAULT NULL;