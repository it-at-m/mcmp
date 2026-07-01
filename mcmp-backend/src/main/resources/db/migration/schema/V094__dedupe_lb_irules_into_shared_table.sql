-- V093 stored one lb_irule row per virtual server, but the same irule (same name + content) is
-- routinely attached to many virtual servers, so that duplicated the script content per VS.
-- Make lb_irule a shared table (unique on name+content) linked via a join table instead.
CREATE TABLE cmp.lb_virtual_server_has_irules (
    lb_virtual_server_id BIGINT NOT NULL REFERENCES cmp.lb_virtual_server(id) ON DELETE CASCADE,
    lb_irule_id           BIGINT NOT NULL REFERENCES cmp.lb_irule(id) ON DELETE CASCADE
);
ALTER TABLE cmp.lb_virtual_server_has_irules OWNER TO cmp;

CREATE TEMP TABLE lb_irule_canonical AS
SELECT name, content, MIN(id) AS canonical_id
FROM cmp.lb_irule
GROUP BY name, content;

INSERT INTO cmp.lb_virtual_server_has_irules (lb_virtual_server_id, lb_irule_id)
SELECT DISTINCT i.lb_virtual_server_id, c.canonical_id
FROM cmp.lb_irule i
JOIN lb_irule_canonical c
  ON c.name = i.name AND c.content IS NOT DISTINCT FROM i.content;

ALTER TABLE cmp.lb_virtual_server_has_irules
    ADD PRIMARY KEY (lb_virtual_server_id, lb_irule_id);
CREATE INDEX idx_lb_vs_has_irules_irule_id ON cmp.lb_virtual_server_has_irules (lb_irule_id);

DELETE FROM cmp.lb_irule i
WHERE i.id NOT IN (SELECT canonical_id FROM lb_irule_canonical);

DROP TABLE lb_irule_canonical;

ALTER TABLE cmp.lb_irule DROP COLUMN lb_virtual_server_id;

-- irule scripts can exceed the btree 1/3-page limit, so index a hash of the content rather
-- than the content itself.
CREATE UNIQUE INDEX uq_lb_irule_name_content ON cmp.lb_irule (name, md5(coalesce(content, '')));
