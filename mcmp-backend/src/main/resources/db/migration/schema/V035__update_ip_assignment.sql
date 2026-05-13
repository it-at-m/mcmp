SET client_encoding = 'UTF8';

DROP TABLE IF EXISTS cmp.ip_assignment;

CREATE TABLE cmp.ip_assignment (
   "nic_id" BIGINT NOT NULL,
   "ip_id" BIGINT NOT NULL,
   CONSTRAINT pk_id_assignment PRIMARY KEY ("nic_id", "ip_id"),
   CONSTRAINT fk_nic_id FOREIGN KEY ("nic_id") REFERENCES cmp.nic("id") ON DELETE CASCADE,
   CONSTRAINT fk_ip_id FOREIGN KEY ("ip_id") REFERENCES cmp.ip("id") ON DELETE CASCADE
);

ALTER TABLE cmp.server ALTER COLUMN "guest_tools_ip_address" TYPE VARCHAR(500);
ALTER TABLE cmp.nic ALTER COLUMN "tools_ip_address" TYPE VARCHAR(500);
ALTER TABLE cmp.job ADD COLUMN "hostname" VARCHAR(30);
