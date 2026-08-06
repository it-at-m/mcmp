SET client_encoding = 'UTF8';

ALTER TABLE kubernetes_cluster
    ADD COLUMN web_console_url TEXT;
