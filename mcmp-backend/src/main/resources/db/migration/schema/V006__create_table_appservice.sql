SET client_encoding = 'UTF8';

CREATE TABLE cmp.appservice (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "version" bigint NOT NULL DEFAULT 0,
    "created_at" TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    "updated_at" TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    "name" VARCHAR(100),
    "number" VARCHAR(100) NOT NULL UNIQUE,
    "sys_id" VARCHAR(100) NOT NULL UNIQUE,
    "used_for" VARCHAR(15) NOT NULL,
    "environment" VARCHAR(15) NOT NULL,
    "owned_by_id" bigint,
    "service_owner_delegate_id" bigint,
    "change_group_id" bigint,
    "enable_vcenterc" BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_owned_by_id FOREIGN KEY ("owned_by_id") REFERENCES cmp.user("id") ON DELETE SET NULL,
    CONSTRAINT fk_service_owner_delegate_id FOREIGN KEY ("service_owner_delegate_id") REFERENCES cmp.user("id") ON DELETE SET NULL,
    CONSTRAINT fk_change_group_id FOREIGN KEY ("change_group_id") REFERENCES cmp.group("id") ON DELETE SET NULL
);
ALTER TABLE cmp.appservice OWNER TO cmp;
