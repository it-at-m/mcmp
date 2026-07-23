SET client_encoding = 'UTF8';

ALTER TABLE cmp.repository
    ADD COLUMN locked BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN repository_url TEXT;
