SET client_encoding = 'UTF8';

ALTER TABLE cmp.server ADD COLUMN maintenance_mode_expires_at timestamp with time zone;
ALTER TABLE cmp.server ADD COLUMN "maintenance_mode" BOOLEAN NOT NULL DEFAULT FALSE;