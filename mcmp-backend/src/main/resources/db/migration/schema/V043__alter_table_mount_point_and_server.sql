SET client_encoding = 'UTF8';

ALTER TABLE cmp.mount_point ADD COLUMN "hidden" BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE cmp.mount_point ADD COLUMN "editable" BOOLEAN DEFAULT TRUE NOT NULL;
ALTER TABLE cmp.mount_point ADD COLUMN "foreman_uuid" VARCHAR(50);
ALTER TABLE cmp.mount_point ADD COLUMN "foreman_capacity_in_bytes" bigint;
ALTER TABLE cmp.mount_point ADD COLUMN "foreman_partition" VARCHAR(255);
ALTER TABLE cmp.mount_point ADD COLUMN "foreman_parttype" VARCHAR(20);
ALTER TABLE cmp.mount_point ADD COLUMN "foreman_partuuid" VARCHAR(50);
ALTER TABLE cmp.mount_point ALTER COLUMN foreman_parttype TYPE VARCHAR(50);

ALTER TABLE cmp.server ALTER COLUMN server_infos_ticket_no TYPE VARCHAR(100);
ALTER TABLE cmp.server ADD COLUMN "operatingsystem" VARCHAR(250);
