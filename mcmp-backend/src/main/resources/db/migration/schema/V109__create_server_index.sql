CREATE INDEX IF NOT EXISTS idx_server_managed_powered_on_oracle_id_fqdn
    ON cmp.server (id)
    INCLUDE (fqdn)
    WHERE managed = true
        AND db_oracle = true
        AND power_state = 'poweredOn';
