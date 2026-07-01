-- Replace lb_virtual_server.irules (JSONB map of arbitrary irule name -> base64 script) with a
-- real table; a virtual server can have any number of irules under any name.
CREATE TABLE cmp.lb_irule (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version              BIGINT      NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ          DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           TIMESTAMPTZ          DEFAULT CURRENT_TIMESTAMP(3),
    lb_virtual_server_id BIGINT      NOT NULL REFERENCES cmp.lb_virtual_server(id) ON DELETE CASCADE,
    name                 VARCHAR(512) NOT NULL,
    content              TEXT,
    UNIQUE (lb_virtual_server_id, name)
);
ALTER TABLE cmp.lb_irule OWNER TO cmp;
CREATE INDEX idx_lb_irule_vs_id ON cmp.lb_irule (lb_virtual_server_id);

INSERT INTO cmp.lb_irule (lb_virtual_server_id, name, content)
SELECT vs.id,
       kv.key,
       convert_from(decode(kv.value, 'base64'), 'UTF8')
FROM cmp.lb_virtual_server vs
CROSS JOIN LATERAL jsonb_each_text(vs.irules) AS kv(key, value)
WHERE vs.irules IS NOT NULL;

ALTER TABLE cmp.lb_virtual_server DROP COLUMN irules;

-- Replace lb_pool.monitors / lb_pool_member.monitors (JSONB arrays) with a real, referenced table.
CREATE TABLE cmp.lb_pool_monitor (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version           BIGINT      NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ          DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        TIMESTAMPTZ          DEFAULT CURRENT_TIMESTAMP(3),
    lb_pool_id        BIGINT REFERENCES cmp.lb_pool(id) ON DELETE CASCADE,
    lb_pool_member_id BIGINT REFERENCES cmp.lb_pool_member(id) ON DELETE CASCADE,
    type              VARCHAR(64) NOT NULL,
    interval_seconds  INTEGER,
    port              VARCHAR(32),
    method            VARCHAR(16),
    path              VARCHAR(512),
    host              VARCHAR(255),
    http_version      VARCHAR(16),
    expect            VARCHAR(512),
    CONSTRAINT chk_lb_pool_monitor_owner CHECK (
        (lb_pool_id IS NOT NULL AND lb_pool_member_id IS NULL)
        OR (lb_pool_id IS NULL AND lb_pool_member_id IS NOT NULL)
    )
);
ALTER TABLE cmp.lb_pool_monitor OWNER TO cmp;
CREATE INDEX idx_lb_pool_monitor_pool_id ON cmp.lb_pool_monitor (lb_pool_id);
CREATE INDEX idx_lb_pool_monitor_pool_member_id ON cmp.lb_pool_monitor (lb_pool_member_id);

INSERT INTO cmp.lb_pool_monitor (lb_pool_id, type, interval_seconds, port, method, path, host, http_version, expect)
SELECT p.id, m ->> 'type', (m ->> 'interval')::integer, m ->> 'port', m ->> 'method',
       m ->> 'path', m ->> 'host', m ->> 'version', m ->> 'expect'
FROM cmp.lb_pool p
CROSS JOIN LATERAL jsonb_array_elements(p.monitors) AS m
WHERE p.monitors IS NOT NULL;

INSERT INTO cmp.lb_pool_monitor (lb_pool_member_id, type, interval_seconds, port, method, path, host, http_version, expect)
SELECT pm.id, m ->> 'type', (m ->> 'interval')::integer, m ->> 'port', m ->> 'method',
       m ->> 'path', m ->> 'host', m ->> 'version', m ->> 'expect'
FROM cmp.lb_pool_member pm
CROSS JOIN LATERAL jsonb_array_elements(pm.monitors) AS m
WHERE pm.monitors IS NOT NULL;

ALTER TABLE cmp.lb_pool DROP COLUMN monitors;
ALTER TABLE cmp.lb_pool_member DROP COLUMN monitors;
