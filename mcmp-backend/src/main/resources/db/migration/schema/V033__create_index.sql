SET client_encoding = 'UTF8';
CREATE INDEX idx_nic_server_vnic_id ON cmp.nic (server_id, vnic_key, id);

ALTER TABLE cmp.disk DROP CONSTRAINT server_vdisk_key_idx;
CREATE INDEX idx_disk_server_vdisk_id ON cmp.disk (server_id, vdisk_key, id);
ALTER TABLE cmp.disk ADD CONSTRAINT uk_disk_server_vdisk_key UNIQUE (server_id, vdisk_key);

CREATE INDEX idx_server_fqdn_covering ON cmp.server (UPPER(fqdn)) INCLUDE (id, name, power_state, guest_config_full_name);

DROP INDEX IF EXISTS cmp.idx_server_name;
CREATE INDEX idx_server_cloud_id ON cmp.server (cloud_id);

CREATE INDEX idx_port_group_cloud_id ON port_group (cloud_id);

CREATE INDEX idx_mount_point_server_disk_id ON mount_point (server_id, disk_path, id);

CREATE INDEX idx_mount_point_server_id_source ON mount_point (server_id, source);



