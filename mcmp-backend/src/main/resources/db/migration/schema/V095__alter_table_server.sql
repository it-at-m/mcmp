SET client_encoding = 'UTF8';

ALTER TABLE cmp.server ADD COLUMN memory_allocation_expandable_reservation BOOLEAN;
ALTER TABLE cmp.server ADD COLUMN memory_allocation_limit BIGINT;
ALTER TABLE cmp.server ADD COLUMN memory_allocation_overhead_limit BIGINT;
ALTER TABLE cmp.server ADD COLUMN memory_allocation_reservation BIGINT;

ALTER TABLE cmp.server ADD COLUMN cpu_allocation_expandable_reservation BOOLEAN;
ALTER TABLE cmp.server ADD COLUMN cpu_allocation_limit BIGINT;
ALTER TABLE cmp.server ADD COLUMN cpu_allocation_overhead_limit BIGINT;
ALTER TABLE cmp.server ADD COLUMN cpu_allocation_reservation BIGINT;


