SET client_encoding = 'UTF8';
ALTER TABLE cmp.backup DROP COLUMN "totalsize";
ALTER TABLE cmp.backup ADD COLUMN "totalsize" BIGINT DEFAULT 0;
ALTER TABLE cmp.backup DROP COLUMN "restrict";
ALTER TABLE cmp.backup DROP COLUMN "mcmp_status";
ALTER TABLE cmp.backup DROP COLUMN "mcmp_network_typ";
ALTER TABLE cmp.backup DROP COLUMN "mcmp_network_group";
ALTER TABLE cmp.network ALTER COLUMN vlan DROP NOT NULL;
ALTER TABLE cmp.network ALTER COLUMN cidr DROP NOT NULL;
ALTER TABLE cmp.network ALTER COLUMN ip_address DROP NOT NULL;
ALTER TABLE cmp.network ALTER COLUMN netmask DROP NOT NULL;
ALTER TABLE cmp.network ALTER COLUMN gateway DROP NOT NULL;
ALTER TABLE cmp.network ALTER COLUMN broadcast DROP NOT NULL;
ALTER TABLE cmp.network ALTER COLUMN name DROP NOT NULL;

ALTER TABLE cmp.network ADD COLUMN "mcmp_status" boolean DEFAULT false NOT NULL;
ALTER TABLE cmp.network ADD COLUMN "mcmp_network_typ" VARCHAR(25);
ALTER TABLE cmp.network ADD COLUMN "mcmp_network_group" VARCHAR(100);

ALTER TABLE cmp.network_group DROP COLUMN "environment";
ALTER TABLE cmp.network_group DROP COLUMN "description";
ALTER TABLE cmp.network_group ADD COLUMN "restrict" boolean DEFAULT false NOT NULL;


