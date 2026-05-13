SET client_encoding = 'UTF8';

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_appservice_name_trgm
ON appservice
USING gin (lower(name) gin_trgm_ops);

ALTER TABLE network DROP CONSTRAINT uq_infoblox_vlan;
ALTER TABLE network ALTER COLUMN vlan TYPE varchar(200);

