SET client_encoding = 'UTF8';

ALTER TABLE cmp.user DROP COLUMN deleted;
ALTER TABLE cmp.user ADD COLUMN "special_role" BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE cmp.config_snow ADD COLUMN is_default BOOLEAN DEFAULT FALSE NOT NULL;
CREATE UNIQUE INDEX idx_config_snow_is_default ON cmp.config_snow (is_default) WHERE is_default = TRUE;
UPDATE cmp.config_snow SET is_default = TRUE WHERE api_description = 'SNOW-PROD';

ALTER TABLE cmp.config_infoblox ADD COLUMN is_default BOOLEAN DEFAULT FALSE NOT NULL;
CREATE UNIQUE INDEX idx_config_infoblox_is_default ON cmp.config_infoblox (is_default) WHERE is_default = TRUE;
UPDATE cmp.config_infoblox SET is_default = TRUE WHERE api_description = 'INFOBLOX-P';