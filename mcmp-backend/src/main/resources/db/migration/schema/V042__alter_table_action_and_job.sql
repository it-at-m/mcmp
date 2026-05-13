SET client_encoding = 'UTF8';

ALTER TABLE cmp.action ADD COLUMN "execution_title" VARCHAR(255);
ALTER TABLE cmp.action ADD COLUMN "execution_description" TEXT;
ALTER TABLE cmp.action ADD COLUMN "success_title" VARCHAR(255);
ALTER TABLE cmp.action ADD COLUMN "success_description" TEXT;

UPDATE cmp.action SET execution_title = title;
UPDATE cmp.action SET execution_description = description;
UPDATE cmp.action SET success_title = title;
UPDATE cmp.action SET success_description = description;

ALTER TABLE cmp.job ADD COLUMN "action_execution_title" VARCHAR(255);
ALTER TABLE cmp.job ADD COLUMN "action_execution_description" TEXT;
ALTER TABLE cmp.job ADD COLUMN "action_success_title" VARCHAR(255);
ALTER TABLE cmp.job ADD COLUMN "action_success_description" TEXT;

UPDATE cmp.job SET action_execution_title = action_title;
UPDATE cmp.job SET action_execution_description = action_description;
UPDATE cmp.job SET action_success_title = action_title;
UPDATE cmp.job SET action_success_description = action_description;
