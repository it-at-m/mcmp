SET client_encoding = 'UTF8';

CREATE TYPE cmp.environment_type AS ENUM ('c', 'd', 'k', 'p', 's', 'tl');
ALTER TABLE cmp.appservice DROP COLUMN "environment" ;
ALTER TABLE cmp.appservice ADD COLUMN "environment" cmp.environment_type NOT NULL;

CREATE TABLE cmp.network_group (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "version" BIGINT DEFAULT 0,
    "created_at" TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    "updated_at" TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    "name" VARCHAR(100),
    "application" boolean DEFAULT false NOT NULL,
    "database" boolean DEFAULT false NOT NULL,
    "storage" boolean DEFAULT false NOT NULL,
    "environment" cmp.environment_type NOT NULL
);
ALTER TABLE cmp.network_group OWNER TO cmp;

CREATE TABLE cmp.appservice_network_group_assignment (
    "appservice_id" BIGINT NOT NULL,
    "network_group_id" BIGINT NOT NULL,
    "without_use_since" TIMESTAMPTZ,
    CONSTRAINT pk_appservice_network_group PRIMARY KEY ("appservice_id", "network_group_id"),
    CONSTRAINT fk_appservice_id FOREIGN KEY ("appservice_id") REFERENCES cmp.appservice("id") ON DELETE CASCADE,
    CONSTRAINT fk_network_group_id FOREIGN KEY ("network_group_id") REFERENCES cmp.network_group("id") ON DELETE CASCADE
);

alter table cmp.appservice_network_group_assignment OWNER TO cmp;

CREATE TABLE cmp.network (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "version" BIGINT DEFAULT 0,
    "created_at" TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    "updated_at" TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    "infoblox_id" bigint NOT NULL,
    "vlan" int NOT NULL,
    "cidr" VARCHAR(50) NOT NULL,
    "ip_address" VARCHAR(50) NOT NULL,
    "netmask" VARCHAR(50) NOT NULL,
    "gateway" VARCHAR(50) NOT NULL,
    "broadcast" VARCHAR(50) NOT NULL,
    "dns_primary" VARCHAR(50),
    "dns_secondary" VARCHAR(50),
    "name" VARCHAR(100) NOT NULL,
    "referat" VARCHAR(50),
    "environment" cmp.environment_type NOT NULL,
    "networktyp" VARCHAR(100),
    "comment" TEXT,
    "network_group_id" bigint,
    CONSTRAINT fk_infoblox_id FOREIGN KEY ("infoblox_id") REFERENCES cmp.config_infoblox("id") ON DELETE CASCADE,
    CONSTRAINT fk_network_group_id FOREIGN KEY ("network_group_id") REFERENCES cmp.network_group("id") ON DELETE SET NULL,
    CONSTRAINT uq_infoblox_vlan UNIQUE (infoblox_id, vlan),
    CONSTRAINT uq_infoblox_ip UNIQUE (infoblox_id, ip_address)
);
ALTER TABLE cmp.network OWNER TO cmp;

ALTER TABLE cmp.port_group ADD COLUMN "network_id" BIGINT;
ALTER TABLE cmp.port_group ADD CONSTRAINT fk_network_id FOREIGN KEY ("network_id") REFERENCES cmp.network("id") ON DELETE SET NULL;
