SET client_encoding = 'UTF8';

DROP INDEX cmp.idx_server_managed_powered_on_oracle_id_fqdn;

CREATE INDEX idx_server_managed_powered_on_oracle_fqdn
    ON cmp.server (fqdn) INCLUDE (oracle_sids)
    WHERE managed = true AND db_oracle = true AND power_state = 'poweredOn';

CREATE INDEX idx_server_managed_powered_on_mysql_fqdn
    ON cmp.server (fqdn)
    WHERE managed = true AND db_mysql = true AND power_state = 'poweredOn';

CREATE INDEX idx_server_managed_powered_on_mariadb_fqdn
    ON cmp.server (fqdn)
    WHERE managed = true AND db_mariadb = true AND power_state = 'poweredOn';

CREATE INDEX idx_server_managed_powered_on_postgres_fqdn
    ON cmp.server (fqdn)
    WHERE managed = true AND db_postgres = true AND power_state = 'poweredOn';

CREATE INDEX idx_server_managed_powered_on_mssql_fqdn
    ON cmp.server (fqdn)
    WHERE managed = true AND db_mssql = true AND power_state = 'poweredOn';

CREATE INDEX idx_server_managed_powered_on_mongodb_fqdn
    ON cmp.server (fqdn)
    WHERE managed = true AND db_mongodb = true AND power_state = 'poweredOn';

CREATE TYPE cmp.tagging_action as enum ('SERVER', 'REPOSITORY', 'LOADBALANCER', 'KUBERNETES_NAMESPACE');
ALTER TYPE  cmp.tagging_action owner to cmp;

ALTER TABLE cmp.action
    ADD COLUMN tagging_action cmp.tagging_action;

UPDATE cmp.action SET tagging_action = 'SERVER' where server_installation = true;

ALTER TABLE cmp.job
    ADD COLUMN tagging_action cmp.tagging_action;

UPDATE cmp.job SET tagging_action = 'SERVER' where server_installation = true;

CREATE INDEX idx_job_server_unfinished
    ON cmp.job (tagging_action)
    INCLUDE (hostname)
    WHERE tagging_action = 'SERVER'
        AND status NOT IN ('successful', 'failed', 'error', 'canceled', 'rejected');

