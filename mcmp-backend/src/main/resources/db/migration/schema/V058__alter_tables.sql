SET client_encoding = 'UTF8';

ALTER TABLE cmp.job ADD COLUMN "non_oss" BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE cmp.user ADD COLUMN dark_mode BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE cmp.user ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE cmp.server_custom_attribute
(
    "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "version" BIGINT NOT NULL DEFAULT 0,
    "created_at" TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    "updated_at" TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    "server_id" BIGINT NOT NULL CONSTRAINT fk_custom_attribute_server REFERENCES cmp.server ON DELETE CASCADE,
    "name" VARCHAR(255),
    "value" TEXT
);
ALTER TABLE cmp.server_custom_attribute OWNER TO cmp;

CREATE TABLE cmp.eai_import_log
(
    "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "version" BIGINT NOT NULL DEFAULT 0,
    "created_at" TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    "updated_at" TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    "eai_name" VARCHAR(255) NOT NULL,
    "last_import" TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    "success" BOOLEAN NOT NULL DEFAULT FALSE,
    "status" TEXT
);
ALTER TABLE cmp.eai_import_log OWNER TO cmp;

CREATE TABLE cmp.user_favorite_server
(
    user_id BIGINT NOT NULL CONSTRAINT fk_favorite_user REFERENCES cmp."user" (id) ON DELETE CASCADE,
    server_id  BIGINT NOT NULL CONSTRAINT fk_favorite_server REFERENCES cmp.server (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, server_id)
);
ALTER TABLE cmp.user_favorite_server OWNER TO cmp;
CREATE INDEX idx_favorite_user_id ON cmp.user_favorite_server (user_id);



