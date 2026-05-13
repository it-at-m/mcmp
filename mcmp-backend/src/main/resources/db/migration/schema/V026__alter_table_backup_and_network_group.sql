SET client_encoding = 'UTF8';

ALTER TABLE cmp.backup ADD COLUMN "ssretent_string" VARCHAR(30);
ALTER TABLE cmp.backup ADD COLUMN "ssretent" timestamptz;
ALTER TABLE cmp.backup ADD COLUMN "totalsize" VARCHAR(25);
ALTER TABLE cmp.backup ADD COLUMN "runtime" VARCHAR(10);

ALTER TABLE cmp.backup ADD COLUMN "restrict" boolean DEFAULT false NOT NULL;
ALTER TABLE cmp.backup ADD COLUMN "mcmp_status" boolean DEFAULT false NOT NULL;
ALTER TABLE cmp.backup ADD COLUMN "mcmp_network_typ" VARCHAR(25);
ALTER TABLE cmp.backup ADD COLUMN "mcmp_network_group" VARCHAR(100);
