SET client_encoding = 'UTF8';

ALTER TABLE cmp.action ADD COLUMN "change_justification" TEXT;
ALTER TABLE cmp.action ADD COLUMN "change_implementation_plan" TEXT;
ALTER TABLE cmp.action ADD COLUMN "change_risk_impact_analysis" TEXT;
ALTER TABLE cmp.action ADD COLUMN "change_backout_plan" TEXT;
ALTER TABLE cmp.action ADD COLUMN "change_close_note_successful" TEXT;
ALTER TABLE cmp.action ADD COLUMN "change_close_note_successful_issues" TEXT;
ALTER TABLE cmp.action ADD COLUMN "change_close_note_unsuccessful" TEXT;
ALTER TABLE cmp.action DROP COLUMN "change_error_title";
ALTER TABLE cmp.action DROP COLUMN "change_error_description";

ALTER TABLE cmp.job ALTER COLUMN "hostname" TYPE VARCHAR(50);
ALTER TABLE cmp.job ADD COLUMN "ip" VARCHAR(45);
ALTER TABLE cmp.job ADD COLUMN "change_justification" TEXT;
ALTER TABLE cmp.job ADD COLUMN "change_implementation_plan" TEXT;
ALTER TABLE cmp.job ADD COLUMN "change_risk_impact_analysis" TEXT;
ALTER TABLE cmp.job ADD COLUMN "change_backout_plan" TEXT;
ALTER TABLE cmp.job ADD COLUMN "change_close_note_successful" TEXT;
ALTER TABLE cmp.job ADD COLUMN "change_close_note_successful_issues" TEXT;
ALTER TABLE cmp.job ADD COLUMN "change_close_note_unsuccessful" TEXT;
ALTER TABLE cmp.job DROP COLUMN "change_error_title";
ALTER TABLE cmp.job DROP COLUMN "change_error_description";

