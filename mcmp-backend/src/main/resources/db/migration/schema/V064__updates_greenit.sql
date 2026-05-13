SET client_encoding = 'UTF8';

CREATE INDEX idx_active_jobs ON "cmp"."job" (is_low_priority, id) WHERE status NOT IN ('successful', 'failed', 'error', 'canceled', 'rejected');

ALTER TABLE cmp.server DROP COLUMN is_power_off_change_pending;
ALTER TABLE cmp.server DROP COLUMN is_rightsizing_change_pending;
ALTER TABLE cmp.server DROP COLUMN greenit_power_off_change_rejected_date;
ALTER TABLE cmp.server DROP COLUMN greenit_rightsizing_change_rejected_date;

ALTER TABLE cmp.server ADD COLUMN greenit_shutdown_change_pending BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE cmp.server ADD COLUMN greenit_shutdown_change_rejected_date TIMESTAMPTZ;
COMMENT ON COLUMN cmp.server.greenit_shutdown_change_rejected_date IS 'Last date when a green-it shutdown change was rejected for this server (stored as TIMESTAMPTZ in Europe/Berlin timezone)';

ALTER TABLE cmp.server ADD COLUMN greenit_rightsizing_change_pending BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE cmp.server ADD COLUMN greenit_rightsizing_change_rejected_date TIMESTAMPTZ;
COMMENT ON COLUMN cmp.server.greenit_rightsizing_change_rejected_date IS 'Last date when a green-it rightsizing change was rejected for this server (stored as TIMESTAMPTZ in Europe/Berlin timezone)';

ALTER TABLE cmp.green_it_power_off RENAME TO green_it_shutdown;
UPDATE cmp.action set identifier = 'GREEN_IT_VMWARE_SHUTDOWN' where identifier = 'GREEN_IT_VMWARE_STOP';
UPDATE cmp.job set action_identifier = 'GREEN_IT_VMWARE_SHUTDOWN' where action_identifier = 'GREEN_IT_VMWARE_STOP';
