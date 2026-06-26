ALTER TABLE ontap_volume ADD COLUMN storage_category VARCHAR(50);
ALTER TABLE ontap_qtree ADD COLUMN storage_category VARCHAR(50);
ALTER TABLE storagegrid_buckets ADD COLUMN storage_category VARCHAR(50);
