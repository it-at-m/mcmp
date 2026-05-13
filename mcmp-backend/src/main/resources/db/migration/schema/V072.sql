SET client_encoding = 'UTF8';

ALTER TABLE cmp.cloud ADD COLUMN green_it_enabled boolean default false not null;

UPDATE cmp.cloud SET green_it_enabled = true WHERE name = 'vcenterk.muenchen.de';
