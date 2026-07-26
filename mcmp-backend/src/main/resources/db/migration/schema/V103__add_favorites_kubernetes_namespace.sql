SET client_encoding = 'UTF8';

CREATE TABLE cmp.user_favorite_kubernetes_namespace
(
    user_id               BIGINT NOT NULL CONSTRAINT fk_favorite_kubernetes_namespace_user REFERENCES cmp."user" (id) ON DELETE CASCADE,
    kubernetes_namespace_id BIGINT NOT NULL CONSTRAINT fk_favorite_kubernetes_namespace_ns REFERENCES cmp.kubernetes_namespace (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, kubernetes_namespace_id)
);
ALTER TABLE cmp.user_favorite_kubernetes_namespace OWNER TO cmp;
CREATE INDEX idx_favorite_kubernetes_namespace_user_id ON cmp.user_favorite_kubernetes_namespace (user_id);
