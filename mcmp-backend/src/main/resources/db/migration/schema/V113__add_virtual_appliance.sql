SET client_encoding = 'UTF8';

ALTER TABLE cmp.server
  ADD COLUMN virtual_appliance BOOLEAN DEFAULT false NOT NULL;