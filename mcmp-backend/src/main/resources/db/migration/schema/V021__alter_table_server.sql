SET client_encoding = 'UTF8';

ALTER TABLE cmp.server ADD COLUMN "locked" BOOLEAN NOT NULL DEFAULT FALSE;
