SET client_encoding = 'UTF8';

CREATE TYPE cmp.ip_type AS ENUM ('IPv4', 'IPv6');

CREATE TABLE cmp.ip (
   "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
   "version" BIGINT DEFAULT 0,
   "created_at" TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
   "updated_at" TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
   "ip" VARCHAR(45) NOT NULL UNIQUE,
   "ip_type" cmp.ip_type NOT NULL,
   "dns_name" VARCHAR(100),
   "dns_mac" VARCHAR(17)
);
ALTER TABLE cmp.ip OWNER TO cmp;

CREATE OR REPLACE FUNCTION cmp.normalize_ip()
   RETURNS TRIGGER AS $$
BEGIN
    IF NEW.ip IS NOT NULL THEN
        NEW.ip = TRIM(NEW.ip);
    END IF;
    IF NEW.ip_type = 'IPv6' AND NEW.ip IS NOT NULL THEN
        NEW.ip = LOWER(NEW.ip);
    END IF;
    IF NEW.dns_name IS NOT NULL THEN
        NEW.dns_name = TRIM(NEW.dns_name);
    END IF;
    IF NEW.dns_mac IS NOT NULL THEN
        NEW.dns_mac = TRIM(UPPER(NEW.dns_mac));
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger für INSERT und UPDATE
CREATE TRIGGER trg_normalize_ip
    BEFORE INSERT OR UPDATE ON cmp.ip
    FOR EACH ROW
EXECUTE FUNCTION cmp.normalize_ip();


CREATE TABLE cmp.ip_assignment (
   "nic_id" BIGINT NOT NULL,
   "ip_id" BIGINT NOT NULL,
   CONSTRAINT pk_id_assignment PRIMARY KEY ("nic_id", "ip_id"),
   CONSTRAINT fk_nic_id FOREIGN KEY ("nic_id") REFERENCES cmp.server("id") ON DELETE CASCADE,
   CONSTRAINT fk_ip_id FOREIGN KEY ("ip_id") REFERENCES cmp.appservice("id") ON DELETE CASCADE
);
ALTER TABLE cmp.ip_assignment OWNER TO cmp;

CREATE TABLE cmp.cname (
                        "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        "version" BIGINT DEFAULT 0,
                        "created_at" TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
                        "updated_at" TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
                        "cname" VARCHAR(45) NOT NULL UNIQUE,
                        "ip_id" bigint NOT NULL,
                        CONSTRAINT fk_cname_ip FOREIGN KEY (ip_id) REFERENCES cmp.ip(id) ON DELETE CASCADE
);
ALTER TABLE cmp.cname OWNER TO cmp;

CREATE OR REPLACE FUNCTION cmp.normalize_cname()
    RETURNS TRIGGER AS $$
BEGIN
    IF NEW.cname IS NOT NULL THEN
        NEW.cname = TRIM(NEW.cname);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger für CNAME Normalisierung bei INSERT und UPDATE
CREATE TRIGGER trg_normalize_cname
    BEFORE INSERT OR UPDATE ON cmp.cname
    FOR EACH ROW
EXECUTE FUNCTION cmp.normalize_cname();