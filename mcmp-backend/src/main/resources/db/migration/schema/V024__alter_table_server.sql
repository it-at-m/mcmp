SET client_encoding = 'UTF8';
ALTER TABLE cmp.server ADD COLUMN "snow_server_name" VARCHAR(100);
ALTER TABLE cmp.server ADD COLUMN "snow_instance_name" VARCHAR(100);
