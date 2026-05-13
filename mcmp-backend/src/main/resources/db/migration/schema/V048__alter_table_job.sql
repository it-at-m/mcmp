SET client_encoding = 'UTF8';

ALTER TABLE cmp.job
    DROP CONSTRAINT fk_snow_id,
    DROP CONSTRAINT fk_awx_id,
    DROP CONSTRAINT fk_server_id,
    DROP CONSTRAINT fk_appservice_id;

ALTER TABLE cmp.job
    ADD CONSTRAINT fk_snow_id
        FOREIGN KEY (snow_id)
            REFERENCES cmp.config_snow (id)
            ON DELETE SET NULL,
    ADD CONSTRAINT fk_awx_id
        FOREIGN KEY (awx_id)
            REFERENCES cmp.config_awx (id)
            ON DELETE SET NULL,
    ADD CONSTRAINT fk_server_id
        FOREIGN KEY (server_id)
            REFERENCES cmp.server (id)
            ON DELETE SET NULL,
    ADD CONSTRAINT fk_appservice_id
        FOREIGN KEY (appservice_id)
            REFERENCES cmp.appservice (id)
            ON DELETE SET NULL;

ALTER TABLE cmp.job ADD COLUMN "quickdiscovery_error_counter" INTEGER DEFAULT 0 NOT NULL ;