CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_server_name_trgm ON cmp.server USING gin (name gin_trgm_ops);
