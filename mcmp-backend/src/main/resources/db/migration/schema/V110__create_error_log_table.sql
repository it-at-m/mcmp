SET client_encoding = 'UTF8';

CREATE TABLE cmp.error_log
(
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version        BIGINT      NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ          DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     TIMESTAMPTZ          DEFAULT CURRENT_TIMESTAMP(3),
    exception_class TEXT       NOT NULL,
    message        TEXT,
    stacktrace     BYTEA,
    request_method TEXT,
    request_path   TEXT,
    request_query  TEXT,
    request_body   TEXT,
    username       TEXT
);
ALTER TABLE cmp.error_log OWNER TO cmp;
CREATE INDEX idx_error_log_created_at ON cmp.error_log (created_at);
