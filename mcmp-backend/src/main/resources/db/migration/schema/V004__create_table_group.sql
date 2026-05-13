SET client_encoding = 'UTF8';

CREATE TABLE cmp.group (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "version" BIGINT DEFAULT 0,
    "created_at" TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    "updated_at" TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    "name" VARCHAR(100) NOT NULL,
    "sys_id" VARCHAR(100) NOT NULL UNIQUE,
    "manager_id" bigint NOT NULL,
    CONSTRAINT fk_manager_id FOREIGN KEY ("manager_id") REFERENCES cmp.user("id") ON DELETE SET NULL
);
ALTER TABLE cmp.group OWNER TO cmp;


