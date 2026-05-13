CREATE INDEX idx_server_maintenance_mode_true ON cmp.server (maintenance_mode) WHERE (maintenance_mode = TRUE);
