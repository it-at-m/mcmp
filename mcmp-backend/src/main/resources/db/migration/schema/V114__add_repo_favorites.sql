SET client_encoding = 'UTF8';

CREATE TABLE cmp.user_favorite_repository
(
    user_id      BIGINT NOT NULL CONSTRAINT fk_favorite_repository_user REFERENCES cmp."user" (id) ON DELETE CASCADE,
    repositoryId BIGINT NOT NULL CONSTRAINT fk_favorite_repository_id REFERENCES cmp.repository (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, repositoryId)
);
ALTER TABLE cmp.user_favorite_repository OWNER TO cmp;
CREATE INDEX idx_favorite_repository_user_id ON cmp.user_favorite_repository (user_id);
