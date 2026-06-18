SET client_encoding = 'UTF8';

ALTER TABLE cmp.ontap_volume_server_mount
    ALTER COLUMN options TYPE JSONB USING to_jsonb(options);

ALTER TABLE cmp.ontap_qtree_server_mount
    ALTER COLUMN options TYPE JSONB USING to_jsonb(options);
