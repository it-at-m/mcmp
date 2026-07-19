SET client_encoding = 'UTF8';

ALTER TABLE cmp.ontap_svm ADD COLUMN snow_name TEXT;
ALTER TABLE cmp.ontap_svm ADD COLUMN snow_sys_id TEXT UNIQUE;
ALTER TABLE cmp.ontap_svm ADD COLUMN snow_sys_class TEXT;
ALTER TABLE cmp.ontap_svm ADD COLUMN snow_last_discovered timestamp with time zone;
CREATE INDEX idx_ontap_svm_swm_uuid ON cmp.ontap_svm (swm_uuid);

ALTER TABLE cmp.ontap_volume ADD COLUMN snow_name TEXT;
ALTER TABLE cmp.ontap_volume ADD COLUMN snow_sys_id TEXT UNIQUE;
ALTER TABLE cmp.ontap_volume ADD COLUMN snow_sys_class TEXT;
ALTER TABLE cmp.ontap_volume ADD COLUMN snow_last_discovered timestamp with time zone;
CREATE INDEX idx_ontap_volume_volume_uuid ON cmp.ontap_volume (volume_uuid);

ALTER TABLE cmp.ontap_qtree ADD COLUMN snow_name TEXT;
ALTER TABLE cmp.ontap_qtree ADD COLUMN snow_sys_id TEXT UNIQUE;
ALTER TABLE cmp.ontap_qtree ADD COLUMN snow_sys_class TEXT;
ALTER TABLE cmp.ontap_qtree ADD COLUMN snow_last_discovered timestamp with time zone;

ALTER TABLE cmp.storagegrid_accounts ADD COLUMN snow_name TEXT;
ALTER TABLE cmp.storagegrid_accounts ADD COLUMN snow_sys_id TEXT UNIQUE;
ALTER TABLE cmp.storagegrid_accounts ADD COLUMN snow_sys_class TEXT;

ALTER TABLE cmp.storagegrid_buckets ADD COLUMN snow_name TEXT;
ALTER TABLE cmp.storagegrid_buckets ADD COLUMN snow_sys_id TEXT UNIQUE;
ALTER TABLE cmp.storagegrid_buckets ADD COLUMN snow_sys_class TEXT;

ALTER TABLE cmp.lb_virtual_server ADD COLUMN snow_name TEXT;
ALTER TABLE cmp.lb_virtual_server ADD COLUMN snow_sys_id TEXT UNIQUE;
ALTER TABLE cmp.lb_virtual_server ADD COLUMN snow_sys_class TEXT;
ALTER TABLE cmp.lb_virtual_server ADD COLUMN snow_last_discovered timestamp with time zone;


