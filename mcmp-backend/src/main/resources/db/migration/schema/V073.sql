SET client_encoding = 'UTF8';

CREATE INDEX idx_job_server_active ON cmp.job (server_id)
    WHERE (status <> ALL
           (ARRAY ['successful'::job_status, 'failed'::job_status, 'error'::job_status, 'canceled'::job_status, 'rejected'::job_status]));
ANALYZE cmp.job;

DELETE FROM cmp.cloud WHERE cloud_type = 'UCS_MANAGER';

CREATE TYPE cmp.server_kind  AS ENUM ('UNKNOWN', 'HARDWARE', 'VIRTUAL');

CREATE TYPE cmp.server_type AS ENUM ('UNKNOWN', 'OTHER', 'CISCO_RACK_UNIT', 'CISCO_BLADE', 'VM_VCENTER', 'VM_PROXMOX', 'VM_OPENSHIFT_VIRTUALIZATION', 'VM_OLVM');

ALTER TABLE cmp.server
    ALTER COLUMN patchnight_change_number DROP DEFAULT,
    ALTER COLUMN patchnight_change_number DROP NOT NULL,
    ADD COLUMN dn TEXT,
    ADD COLUMN association TEXT,
    ADD COLUMN memory_speed INTEGER,
    ADD COLUMN memory_mb_available INTEGER,
    ADD COLUMN mfg_time timestamp with time zone,
    ADD COLUMN model TEXT,
    ADD COLUMN num_of_adaptors INTEGER,
    ADD COLUMN num_of_cores_enabled INTEGER,
    ADD COLUMN num_of_eth_host_ifs INTEGER,
    ADD COLUMN num_of_fc_host_ifs INTEGER,
    ADD COLUMN oper_state TEXT,
    ADD COLUMN ucsm_chassis_id INTEGER,
    ADD COLUMN ucsm_chassis_slot_id INTEGER,
    ADD COLUMN ucsm_server_id INTEGER,
    ADD COLUMN vendor TEXT,
    ADD COLUMN vid TEXT,
    ADD COLUMN server_kind cmp.server_kind DEFAULT 'UNKNOWN' NOT NULL,
    ADD COLUMN server_type cmp.server_type DEFAULT 'UNKNOWN' NOT NULL;
