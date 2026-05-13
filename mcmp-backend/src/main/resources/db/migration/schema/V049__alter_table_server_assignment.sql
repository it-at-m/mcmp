SET client_encoding = 'UTF8';

ALTER TABLE cmp.server_assignment ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;