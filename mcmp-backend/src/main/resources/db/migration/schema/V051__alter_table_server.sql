SET client_encoding = 'UTF8';

ALTER TABLE cmp.server ADD COLUMN foreman_source VARCHAR(50);

UPDATE cmp.server SET foreman_source = 'deployp001.srv.muenchen.de' where foreman_id is not null;
