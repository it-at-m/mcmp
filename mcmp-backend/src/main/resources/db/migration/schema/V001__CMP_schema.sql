SET client_encoding = 'UTF8';

CREATE SCHEMA IF NOT EXISTS cmp;
ALTER SCHEMA cmp OWNER TO cmp;

CREATE TABLE cmp.cloud (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "version" bigint NOT NULL DEFAULT 0,
    "created_at" timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    "updated_at" timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    "name" VARCHAR(100),
    "fqdn" VARCHAR(100) NOT NULL UNIQUE,
    "server_gui" VARCHAR(100)
);
ALTER TABLE cmp.cloud OWNER TO cmp;

CREATE TYPE cmp.server_status_type AS ENUM ('green', 'yellow', 'red', 'gray');
CREATE TYPE cmp.server_rightsizing_type AS ENUM ('oversized', 'ok', 'undersized');
CREATE TABLE cmp.server (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "version" bigint NOT NULL DEFAULT 0,
    "created_at" timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    "updated_at" timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    cloud_id bigint NOT NULL,
    uuid character varying(50) NOT NULL,
    instance_uuid character varying(50),
    vm_id character varying(50),
    "cluster" character varying(25),
    host character varying(50),
    location character varying(10),
    "name" character varying(200) NOT NULL,
    power_state character varying(20) NOT NULL,
    memory_mb integer NOT NULL,
    memory_mb_prev integer,
    memory_mb_change_date timestamp with time zone,
    memory_mb_rightsizing cmp.server_rightsizing_type DEFAULT 'ok' NOT NULL,
    memory_mb_recommended integer DEFAULT '0',
    memory_usage_mb integer DEFAULT 0,
    memory_usage_perc smallint DEFAULT 0,
    memory_usage_perc_avg double precision DEFAULT '0',
    num_cpu integer NOT NULL,
    num_cpu_prev integer,
    num_cpu_change_date timestamp with time zone,
    num_cpu_rightsizing cmp.server_rightsizing_type DEFAULT 'ok',
    num_cpu_recommended integer DEFAULT '0',
    num_cores_per_socket integer,
    cpu_max_usage integer DEFAULT 0,
    cpu_usage integer DEFAULT 0,
    cpu_usage_perc smallint DEFAULT 0,
    cpu_usage_perc_avg double precision DEFAULT '0',
    memory_hot_add_enabled boolean DEFAULT false NOT NULL,
    cpu_hot_add_enabled boolean DEFAULT false NOT NULL,
    cpu_hot_remove_enabled boolean DEFAULT false NOT NULL,
    cpu_topology character varying(100),
    vmx_version character varying(10),
    overall_status cmp.server_status_type DEFAULT 'gray' NOT NULL,
    config_status cmp.server_status_type DEFAULT 'gray' NOT NULL,
    config_equals_tools boolean DEFAULT false NOT NULL,
    guest_config_id character varying(50),
    guest_config_full_name character varying(50),
    guest_tools_id character varying(50),
    guest_tools_full_name character varying(250),
    guest_tools_state character varying(30),
    guest_tools_running_status character varying(30),
    guest_tools_version_status character varying(30),
    guest_tools_version_status2 character varying(30),
    guest_tools_install_type character varying(30),
    guest_tools_version character varying(20),
    guest_tools_family character varying(50),
    guest_tools_hostname character varying(200),
    guest_tools_ip_address character varying(300),
    guest_tools_architecture character varying(10),
    guest_tools_bitness character varying(10),
    guest_tools_build_number character varying(20),
    guest_tools_cpe_string character varying(100),
    guest_tools_distro_addl_version character varying(50),
    guest_tools_distro_name character varying(100),
    guest_tools_distro_version character varying(20),
    guest_tools_family_name character varying(20),
    guest_tools_kernel_version character varying(40),
    guest_tools_pretty_name character varying(100),
    vdisks smallint DEFAULT 0,
    vdisks_capacity_in_bytes bigint DEFAULT 0,
    boot_time timestamp with time zone,
    CONSTRAINT fk_server_cloud FOREIGN KEY ("cloud_id") REFERENCES cmp.cloud(id) ON DELETE CASCADE,
    CONSTRAINT server_cloud_uuid_idx UNIQUE (cloud_id, uuid)
);
ALTER TABLE cmp.server OWNER TO cmp;

CREATE TYPE cmp.backup_type AS ENUM ('vm', 'agent', 'da', 'db', 'dh', 'dm', 'dp', 'ds', 'dy', 'nfs', 'cifs');
CREATE TABLE cmp.backup (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "version" bigint NOT NULL DEFAULT 0,
    "created_at" timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    "updated_at" timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    "server_id" bigint NOT NULL,
    "backup_type" cmp.backup_type NOT NULL,
    "backup_server" varchar(100) NOT NULL,
    "client_server" varchar(100) NOT NULL,
    "save_set_name" varchar(100) NOT NULL,
    "save_time_string" varchar(50) NOT NULL,
    "save_time" timestamptz NOT NULL,
    "ssid" varchar(50) NOT NULL,
    "clone_id" varchar(50) NOT NULL,
    "pool" varchar(100) NOT NULL,
    CONSTRAINT "fk_backup_server" FOREIGN KEY ("server_id") REFERENCES cmp.server(id) ON DELETE CASCADE
);
ALTER TABLE cmp.backup OWNER TO cmp;


CREATE TABLE cmp.disk (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "version" bigint NOT NULL DEFAULT 0,
    "created_at" timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    "updated_at" timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    server_id bigint NOT NULL,
    vdisk_key integer NOT NULL,
    unit_number integer,
    disk_provisioning character varying(50),
    file_name character varying(200),
    capacity_in_bytes bigint,
    vdisk_id character varying(100),
    device character varying(200),
    virtual_disk_format character varying(30),
    disk_mode character varying(30),
    CONSTRAINT fk_disk_server FOREIGN KEY (server_id) REFERENCES cmp.server(id) ON DELETE CASCADE,
    CONSTRAINT server_vdisk_key_idx UNIQUE (server_id, vdisk_key)
);
ALTER TABLE cmp.disk OWNER TO cmp;


CREATE TABLE cmp.mount_point (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "version" bigint NOT NULL DEFAULT 0,
    "created_at" timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    "updated_at" timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    server_id bigint NOT NULL,
    disk_path character varying(255) NOT NULL,
    capacity_in_bytes bigint,
    free_space_in_bytes bigint,
    filesystem_type character varying(20),
    source character varying(10),
    CONSTRAINT fk_mount_point_server FOREIGN KEY (server_id) REFERENCES cmp.server(id) ON DELETE CASCADE,
    CONSTRAINT server_disk_path_idx UNIQUE (server_id, disk_path)
);
ALTER TABLE cmp.mount_point OWNER TO cmp;




CREATE TABLE cmp.port_group (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "version" bigint NOT NULL DEFAULT 0,
    "created_at" timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    "updated_at" timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    cloud_id bigint NOT NULL,
    port_group_key character varying(100) NOT NULL,
    "name" character varying(50),
    vlan character varying(200),
    CONSTRAINT fk_port_group_cloud FOREIGN KEY (cloud_id) REFERENCES cmp.cloud(id) ON DELETE CASCADE,
    CONSTRAINT cloud_port_group_key_idx UNIQUE (cloud_id, port_group_key)
);
ALTER TABLE cmp.port_group OWNER TO cmp;

CREATE TABLE cmp.nic (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "version" bigint NOT NULL DEFAULT 0,
    "created_at" timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    "updated_at" timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    server_id bigint NOT NULL,
    port_group_id bigint,
    vnic_key integer NOT NULL,
    unit_number integer,
    device character varying(200),
    mac_address character varying(50),
    network character varying(200),
    connected boolean DEFAULT false NOT NULL,
    port_group_summary character varying(200),
    port_group_key character varying(200),
    distributed_port_key character varying(200),
    address_type character varying(20),
    card_type character varying(20),
    tools_ip_address character varying(300),
    tools_network_name character varying(200),
    tools_connected boolean DEFAULT false NOT NULL,
   CONSTRAINT fk_nic_server FOREIGN KEY (server_id) REFERENCES cmp.server(id) ON DELETE CASCADE,
   CONSTRAINT fk_nic_port_group FOREIGN KEY (port_group_id) REFERENCES cmp.port_group(id) ON DELETE CASCADE
);
ALTER TABLE cmp.nic OWNER TO cmp;

CREATE TABLE cmp.snapshot (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "version" bigint NOT NULL DEFAULT 0,
    "created_at" timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    "updated_at" timestamptz DEFAULT CURRENT_TIMESTAMP(3),
    server_id bigint NOT NULL,
    snapshot_id integer NOT NULL,
    "name" character varying(200),
    description character varying(200),
    create_time timestamp with time zone,
    quiesced boolean DEFAULT false NOT NULL,
    state character varying(20),
    replay_supported boolean DEFAULT false NOT NULL,
    CONSTRAINT fk_snapshot_server FOREIGN KEY (server_id) REFERENCES cmp.server(id) ON DELETE CASCADE,
    CONSTRAINT server_snapshot_idx UNIQUE (server_id, snapshot_id)
);
ALTER TABLE cmp.snapshot OWNER TO cmp;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE cmp.config_infoblox (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    api_description VARCHAR(100),
    api_username VARCHAR(100) NOT NULL,
    api_password_encrypted BYTEA NOT NULL,
    api_endpoint varchar(500) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE
);
ALTER TABLE cmp.config_infoblox OWNER TO cmp;

CREATE TABLE cmp.config_awx (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    api_description VARCHAR(100),
    api_username VARCHAR(100) NOT NULL,
    api_password_encrypted BYTEA NOT NULL,
    api_endpoint varchar(500) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE
);
ALTER TABLE cmp.config_awx OWNER TO cmp;

CREATE TABLE cmp.config_snow (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    api_description VARCHAR(100),
    api_username VARCHAR(100) NOT NULL,
    api_password_encrypted BYTEA NOT NULL,
    api_endpoint varchar(500) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    proxy varchar(500) NOT NULL,
    use_proxy BOOLEAN NOT NULL DEFAULT FALSE
);
ALTER TABLE cmp.config_snow OWNER TO cmp;

REVOKE USAGE ON SCHEMA public FROM PUBLIC;


