SET client_encoding = 'UTF8';

ALTER TABLE cmp.job ADD COLUMN "notification" boolean DEFAULT false NOT NULL;

