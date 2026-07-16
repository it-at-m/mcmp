SET client_encoding = 'UTF8';

ALTER TABLE cmp.server ADD COLUMN managed_middleware_filebeat BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE cmp.server ADD COLUMN managed_middleware_httpd BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE cmp.server ADD COLUMN managed_middleware_java BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE cmp.server ADD COLUMN managed_middleware_php BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE cmp.server ADD COLUMN managed_middleware_tomcat BOOLEAN NOT NULL DEFAULT FALSE;
