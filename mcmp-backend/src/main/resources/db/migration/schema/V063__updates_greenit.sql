SET client_encoding = 'UTF8';

ALTER TABLE cmp.action
    ALTER COLUMN identifier TYPE text,
    ALTER COLUMN title TYPE text,
    ALTER COLUMN error_title TYPE text,
    ALTER COLUMN change_type TYPE text,
    ALTER COLUMN change_template TYPE text,
    ALTER COLUMN awx_credentials TYPE text,
    ALTER COLUMN awx_extra_vars TYPE text,
    ALTER COLUMN awx_job_type TYPE text,
    ALTER COLUMN awx_limit TYPE text,
    ALTER COLUMN awx_job_tags TYPE text,
    ALTER COLUMN awx_skip_tags TYPE text,
    ALTER COLUMN awx_scm_branch TYPE text,
    ALTER COLUMN awx_instance_groups TYPE text,
    ALTER COLUMN awx_labels TYPE text,
    ALTER COLUMN execution_title TYPE text,
    ALTER COLUMN success_title TYPE text,
    ALTER COLUMN change_action TYPE text;

ALTER TABLE cmp.job
    ALTER COLUMN action_identifier TYPE text,
    ALTER COLUMN action_title TYPE text,
    ALTER COLUMN awx_job_link TYPE text,
    ALTER COLUMN action_error_title TYPE text,
    ALTER COLUMN action_execution_title TYPE text,
    ALTER COLUMN action_success_title TYPE text,
    ALTER COLUMN change_type TYPE text,
    ALTER COLUMN change_template TYPE text,
    ALTER COLUMN change_number TYPE text,
    ALTER COLUMN change_sys_id TYPE text,
    ALTER COLUMN change_link TYPE text,
    ALTER COLUMN change_action TYPE text,
    ALTER COLUMN awx_credentials TYPE text,
    ALTER COLUMN awx_job_type TYPE text,
    ALTER COLUMN awx_limit TYPE text,
    ALTER COLUMN awx_job_tags TYPE text,
    ALTER COLUMN awx_skip_tags TYPE text,
    ALTER COLUMN awx_scm_branch TYPE text,
    ALTER COLUMN awx_instance_groups TYPE text,
    ALTER COLUMN awx_labels TYPE text,
    ALTER COLUMN title TYPE text,
    ALTER COLUMN hostname TYPE text,
    ALTER COLUMN ip TYPE text,
    ALTER COLUMN quickdiscovery_ci_sysid TYPE text,
    ALTER COLUMN quickdiscovery_ci_name TYPE text;

UPDATE cmp.action SET change_justification = null WHERE TRIM(UPPER(change_justification)) = 'TODO' OR change_justification = '';
UPDATE cmp.action SET change_implementation_plan = null WHERE TRIM(UPPER(change_implementation_plan)) = 'TODO' OR change_implementation_plan = '';
UPDATE cmp.action SET change_risk_impact_analysis = null WHERE TRIM(UPPER(change_risk_impact_analysis)) = 'TODO' OR change_risk_impact_analysis = '';
UPDATE cmp.action SET change_backout_plan = null WHERE TRIM(UPPER(change_backout_plan)) = 'TODO' OR change_backout_plan = '';
UPDATE cmp.action SET change_justification = null, change_implementation_plan = null, change_risk_impact_analysis = null, change_backout_plan = null, change_action = null WHERE LOWER(change_type) = 'standard';
UPDATE cmp.action SET change_template = null WHERE LOWER(change_type) = 'normal';

UPDATE cmp.job SET change_justification = null WHERE TRIM(UPPER(change_justification)) = 'TODO' OR change_justification = '';
UPDATE cmp.job SET change_implementation_plan = null WHERE TRIM(UPPER(change_implementation_plan)) = 'TODO' OR change_implementation_plan = '';
UPDATE cmp.job SET change_risk_impact_analysis = null WHERE TRIM(UPPER(change_risk_impact_analysis)) = 'TODO' OR change_risk_impact_analysis = '';
UPDATE cmp.job SET change_backout_plan = null WHERE TRIM(UPPER(change_backout_plan)) = 'TODO' OR change_backout_plan = '';
UPDATE cmp.job SET change_justification = null, change_implementation_plan = null, change_risk_impact_analysis = null, change_backout_plan = null, change_action = null WHERE LOWER(change_type) = 'standard';
UPDATE cmp.job SET change_template = null WHERE LOWER(change_type) = 'normal';