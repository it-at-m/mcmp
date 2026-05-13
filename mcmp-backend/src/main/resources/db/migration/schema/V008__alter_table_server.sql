SET client_encoding = 'UTF8';

ALTER TABLE cmp.server ADD COLUMN "managed" BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE cmp.server ADD COLUMN "fqdn" VARCHAR(100);
ALTER TABLE cmp.server ADD COLUMN "forman_id" bigint;
