SET client_encoding = 'UTF8';

ALTER TABLE cmp.ontap_export_policy_rule
    ALTER COLUMN clients      TYPE JSONB USING to_jsonb(clients),
    ALTER COLUMN protocols    TYPE JSONB USING to_jsonb(protocols),
    ALTER COLUMN rw_rules     TYPE JSONB USING to_jsonb(rw_rules),
    ALTER COLUMN ro_rules     TYPE JSONB USING to_jsonb(ro_rules);
