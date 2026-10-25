SET client_encoding = 'UTF8';

CREATE TABLE config_foreman (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    api_description TEXT,
    api_username TEXT NOT NULL,
    api_password_encrypted BYTEA NOT NULL,
    api_endpoint TEXT NOT NULL,
    fqdn text not null,
    enabled BOOLEAN NOT NULL DEFAULT FALSE
);
ALTER TABLE config_foreman OWNER TO cmp;
CREATE UNIQUE INDEX uq_config_foreman_api_endpoint_lower ON config_foreman (LOWER(api_endpoint));
CREATE UNIQUE INDEX uq_config_foreman_fqdn_lower ON config_foreman (LOWER(fqdn));

CREATE TABLE foreman_host (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    config_foreman_id BIGINT NOT NULL,
    foreman_id BIGINT NOT NULL,
    name TEXT,
    hostname TEXT,
    mac TEXT,
    operatingsystem TEXT,
    uuid TEXT,
    dmi_product_uuid TEXT,
    dmi_product_serial_number TEXT,
    CONSTRAINT fk_foreman_host_config_foreman FOREIGN KEY (config_foreman_id) REFERENCES config_foreman(id) ON DELETE CASCADE,
    CONSTRAINT uq_foreman_host_config_foreman_id_foreman_id UNIQUE (config_foreman_id, foreman_id)
);
ALTER TABLE foreman_host OWNER TO cmp;

CREATE TABLE foreman_interface (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    foreman_host_id BIGINT NOT NULL,
    execution BOOLEAN NOT NULL DEFAULT FALSE,
    fqdn TEXT,
    foreman_id BIGINT NOT NULL,
    identifier TEXT,
    ip TEXT,
    ip6 TEXT,
    mac TEXT,
    mtu INT,
    managed BOOLEAN NOT NULL DEFAULT FALSE,
    name TEXT,
    "primary" BOOLEAN NOT NULL DEFAULT FALSE,
    provision BOOLEAN NOT NULL DEFAULT FALSE,
    subnet_name TEXT,
    type TEXT,
    virtual BOOLEAN NOT NULL DEFAULT FALSE,
    linux BOOLEAN NOT NULL DEFAULT FALSE,
    windows BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_foreman_interface_foreman_host FOREIGN KEY (foreman_host_id) REFERENCES foreman_host(id) ON DELETE CASCADE,
    CONSTRAINT uq_foreman_interface_foreman_host_id_foreman_id UNIQUE (foreman_host_id, foreman_id)
);
ALTER TABLE foreman_interface OWNER TO cmp;

ALTER TABLE server
    ADD COLUMN foreman_host_id BIGINT,
    ADD CONSTRAINT fk_server_foreman_host FOREIGN KEY (foreman_host_id) REFERENCES foreman_host(id) ON DELETE SET NULL,
    ADD CONSTRAINT uq_server_foreman_host_id UNIQUE (foreman_host_id),
    ADD COLUMN oracle_sids TEXT[] NOT NULL DEFAULT '{}'::text[];
