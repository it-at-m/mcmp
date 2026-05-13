SET client_encoding = 'UTF8';

DROP TABLE IF EXISTS cmp.storagegrid_account_sync_rules CASCADE;
DROP TABLE IF EXISTS cmp.storagegrid_account_federations CASCADE;

ALTER TABLE cmp.storagegrid_accounts DROP COLUMN description;
ALTER TABLE cmp.storagegrid_accounts DROP COLUMN capabilities;

CREATE INDEX idx_storagegrid_accounts_config_storagegrid_id ON storagegrid_accounts(config_storagegrid_id);
CREATE INDEX idx_storagegrid_buckets_storagegrid_account_id ON storagegrid_buckets(storagegrid_account_id);

