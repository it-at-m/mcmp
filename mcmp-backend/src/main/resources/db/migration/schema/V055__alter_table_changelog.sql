SET client_encoding = 'UTF8';

ALTER TABLE cmp.changelog ADD COLUMN "is_published" BOOLEAN NOT NULL DEFAULT FALSE;