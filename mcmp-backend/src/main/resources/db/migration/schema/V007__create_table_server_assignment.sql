SET client_encoding = 'UTF8';

CREATE TABLE cmp.server_assignment (
    "server_id" BIGINT NOT NULL,
    "appservice_id" BIGINT NOT NULL,
    CONSTRAINT pk_server_assignment PRIMARY KEY ("server_id", "appservice_id"),
    CONSTRAINT fk_server_id FOREIGN KEY ("server_id") REFERENCES cmp.server("id") ON DELETE CASCADE,
    CONSTRAINT fk_appservice_id FOREIGN KEY ("appservice_id") REFERENCES cmp.appservice("id") ON DELETE CASCADE
);
ALTER TABLE cmp.server_assignment OWNER TO cmp;
