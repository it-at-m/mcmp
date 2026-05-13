SET client_encoding = 'UTF8';

ALTER TABLE cmp.action ADD COLUMN "change_action" VARCHAR(64);
ALTER TABLE cmp.job ADD COLUMN "change_action" VARCHAR(64);

UPDATE cmp.action SET "change_action" = 'other' WHERE "change_type" = 'normal';
UPDATE cmp.action SET "change_action" = null WHERE "change_type" = 'standard';

ALTER TYPE cmp.job_status ADD VALUE 'waiting_for_awx_enablement';
ALTER TYPE cmp.job_status ADD VALUE 'waiting_for_awx_configuration';
ALTER TYPE cmp.job_status ADD VALUE 'waiting_for_service_now_enablement';
ALTER TYPE cmp.job_status ADD VALUE 'waiting_for_service_now_configuration';

ALTER TYPE cmp.awx_status ADD VALUE 'waiting_for_awx_enablement';
ALTER TYPE cmp.awx_status ADD VALUE 'waiting_for_awx_configuration';

ALTER TYPE cmp.change_status ADD VALUE 'waiting_for_service_now_enablement';
ALTER TYPE cmp.change_status ADD VALUE 'waiting_for_service_now_configuration';

ALTER TYPE cmp.quickdiscovery_status ADD VALUE 'waiting_for_service_now_enablement';
ALTER TYPE cmp.quickdiscovery_status ADD VALUE 'waiting_for_service_now_configuration';
