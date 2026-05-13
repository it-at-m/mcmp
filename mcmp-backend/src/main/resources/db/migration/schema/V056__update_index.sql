CREATE EXTENSION IF NOT EXISTS pg_trgm;
DROP INDEX IF EXISTS cmp.idx_appservice_name_trgm;
CREATE INDEX idx_appservice_name_trgm ON cmp.appservice USING gin (lower(name) gin_trgm_ops);
CREATE INDEX idx_appservice_name_sort ON cmp.appservice (name ASC);