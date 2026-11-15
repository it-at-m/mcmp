SET client_encoding = 'UTF8';

ALTER TABLE cmp.job
  ADD COLUMN change_error_counter INTEGER NOT NULL DEFAULT 0;

ALTER TABLE cmp.job_incident
    ADD COLUMN retry_on_resolve boolean NOT NULL DEFAULT false;