SET client_encoding = 'UTF8';
ALTER TABLE cmp.server ALTER COLUMN "patchnight_change_number" TYPE VARCHAR(20);
CREATE INDEX IF NOT EXISTS idx_nic_mac_address_lower ON cmp.nic (LOWER(mac_address));
CREATE INDEX IF NOT EXISTS idx_cloud_id ON cmp.cloud (id);
CREATE INDEX IF NOT EXISTS idx_user_username_covering ON cmp.user (username) INCLUDE (id);
CREATE INDEX IF NOT EXISTS idx_server_assignment_composite ON cmp.server_assignment (server_id, appservice_id);
CREATE INDEX IF NOT EXISTS idx_group_membership_composite ON cmp.group_membership (group_id, user_id);
CREATE INDEX IF NOT EXISTS idx_appservice_change_group ON cmp.appservice (change_group_id);
CREATE INDEX IF NOT EXISTS idx_server_name ON cmp.server (name);
CREATE INDEX IF NOT EXISTS idx_appservice_name_sorted ON cmp.appservice (id, name);
CREATE INDEX IF NOT EXISTS idx_server_name_covering ON cmp.server (name) INCLUDE (id, power_state, guest_config_full_name);
CREATE INDEX IF NOT EXISTS idx_appservice_covering ON cmp.appservice (id) INCLUDE (name, sys_id);
CREATE INDEX IF NOT EXISTS idx_snapshot_server_id ON cmp.snapshot (server_id);
CREATE INDEX IF NOT EXISTS idx_nic_server_id ON cmp.nic (server_id);
CREATE INDEX IF NOT EXISTS idx_job_server_status ON cmp.job USING btree (server_id, status) WHERE server_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_job_user_status ON cmp.job USING btree (user_id, status);
CREATE INDEX IF NOT EXISTS idx_job_awx_id ON cmp.job USING btree (awx_id);
CREATE INDEX IF NOT EXISTS idx_job_status ON cmp.job USING btree (status);

