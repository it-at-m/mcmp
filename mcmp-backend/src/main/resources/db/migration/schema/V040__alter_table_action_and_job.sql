SET client_encoding = 'UTF8';

UPDATE cmp.action SET title = awx_title;
UPDATE cmp.action SET description = awx_description;
UPDATE cmp.action SET error_title = awx_error_title;
UPDATE cmp.action SET error_description = awx_error_description;

ALTER TABLE cmp.action DROP COLUMN awx_title;
ALTER TABLE cmp.action DROP COLUMN awx_description;
ALTER TABLE cmp.action DROP COLUMN awx_error_title;
ALTER TABLE cmp.action DROP COLUMN awx_error_description;

ALTER TABLE cmp.action DROP COLUMN change_title;
ALTER TABLE cmp.action DROP COLUMN change_description;
ALTER TABLE cmp.action DROP COLUMN change_close_note_successful;
ALTER TABLE cmp.action DROP COLUMN change_close_note_successful_issues;
ALTER TABLE cmp.action DROP COLUMN change_close_note_unsuccessful;

ALTER TABLE cmp.job DROP COLUMN awx_title;
ALTER TABLE cmp.job DROP COLUMN awx_description;
ALTER TABLE cmp.job DROP COLUMN awx_error_title;
ALTER TABLE cmp.job DROP COLUMN awx_error_description;

ALTER TABLE cmp.job DROP COLUMN change_title;
ALTER TABLE cmp.job DROP COLUMN change_description;
ALTER TABLE cmp.job DROP COLUMN change_close_note_successful;
ALTER TABLE cmp.job DROP COLUMN change_close_note_successful_issues;
ALTER TABLE cmp.job DROP COLUMN change_close_note_unsuccessful;

ALTER TABLE cmp.job ADD COLUMN tagging_error text;

ALTER TABLE cmp.action ALTER COLUMN snow_id DROP NOT NULL;
ALTER TABLE cmp.action ALTER COLUMN awx_id DROP NOT NULL;
ALTER TABLE cmp.job ALTER COLUMN snow_id DROP NOT NULL;
ALTER TABLE cmp.job ALTER COLUMN awx_id DROP NOT NULL;
