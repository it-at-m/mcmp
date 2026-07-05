-- Replace lb_virtual_server.pool_refs (JSONB map keyed by pool name) with an indexed FK join table.
CREATE TABLE cmp.lb_virtual_server_pool_ref (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version              BIGINT      NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ          DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           TIMESTAMPTZ          DEFAULT CURRENT_TIMESTAMP(3),
    lb_virtual_server_id BIGINT      NOT NULL REFERENCES cmp.lb_virtual_server(id) ON DELETE CASCADE,
    lb_pool_id           BIGINT      NOT NULL REFERENCES cmp.lb_pool(id) ON DELETE CASCADE,
    is_default           BOOLEAN,
    hosts                JSONB,
    paths                JSONB,
    UNIQUE (lb_virtual_server_id, lb_pool_id)
);
ALTER TABLE cmp.lb_virtual_server_pool_ref OWNER TO cmp;
CREATE INDEX idx_lb_vs_pool_ref_vs_id ON cmp.lb_virtual_server_pool_ref (lb_virtual_server_id);
CREATE INDEX idx_lb_vs_pool_ref_pool_id ON cmp.lb_virtual_server_pool_ref (lb_pool_id);

-- Backfill from the existing JSONB map, matching pools by name (the only key available in the old data).
INSERT INTO cmp.lb_virtual_server_pool_ref (lb_virtual_server_id, lb_pool_id, is_default, hosts, paths)
SELECT vs.id,
       p.id,
       (ref.value ->> 'isDefault')::boolean,
       ref.value -> 'hosts',
       ref.value -> 'paths'
FROM cmp.lb_virtual_server vs
CROSS JOIN LATERAL jsonb_each(vs.pool_refs) AS ref(key, value)
JOIN cmp.lb_pool p ON p.name = ref.key
WHERE vs.pool_refs IS NOT NULL;

ALTER TABLE cmp.lb_virtual_server DROP COLUMN pool_refs;
