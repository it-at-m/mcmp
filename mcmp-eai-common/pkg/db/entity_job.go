package db

import (
	"time"
)

type Job struct {
	ID                         int64                `gorm:"column:id;primaryKey;autoIncrement" json:"id"`
	Version                    int64                `gorm:"column:version;default:0" json:"version"`
	CreatedAt                  time.Time            `gorm:"column:created_at;default:CURRENT_TIMESTAMP" json:"created_at"`
	UpdatedAt                  time.Time            `gorm:"column:updated_at;default:CURRENT_TIMESTAMP" json:"updated_at"`
	SnowID                     *int64               `gorm:"column:snow_id;constraint:fk_snow_id,OnDelete:SET NULL" json:"snow_id"`
	AwxID                      *int64               `gorm:"column:awx_id;constraint:fk_awx_id,OnDelete:SET NULL" json:"awx_id"`
	UserID                     *int64               `gorm:"column:user_id;constraint:fk_user_id,OnDelete:SET NULL" json:"user_id"`
	ServerID                   *int64               `gorm:"column:server_id;constraint:fk_server_id,OnDelete:SET NULL" json:"server_id"`
	AppserviceID               *int64               `gorm:"column:appservice_id;constraint:fk_appservice_id,OnDelete:SET NULL" json:"appservice_id"`
	Status                     JobStatus            `gorm:"column:status;not null;default:'new'" json:"status"`
	Title                      *string              `gorm:"column:title" json:"title"`
	Description                *string              `gorm:"column:description;type:text" json:"description"`
	ActionIdentifier           string               `gorm:"column:action_identifier;type:text;not null" json:"action_identifier"`
	ActionTitle                *string              `gorm:"column:action_title;type:text" json:"action_title"`
	ActionDescription          *string              `gorm:"column:action_description;type:text" json:"action_description"`
	ActionErrorTitle           *string              `gorm:"column:action_error_title;type:text" json:"action_error_title"`
	ActionErrorDescription     *string              `gorm:"column:action_error_description;type:text" json:"action_error_description"`
	ActionExecutionTitle       *string              `gorm:"column:action_execution_title;type:text" json:"action_execution_title"`
	ActionExecutionDescription *string              `gorm:"column:action_execution_description;type:text" json:"action_execution_description"`
	ActionSuccessTitle         *string              `gorm:"column:action_success_title;type:text" json:"action_success_title"`
	ActionSuccessDescription   *string              `gorm:"column:action_success_description;type:text" json:"action_success_description"`
	QuickDiscovery             bool                 `gorm:"column:quickdiscovery;not null" json:"quickdiscovery"`
	ServerInstallation         bool                 `gorm:"column:server_installation;not null" json:"server_installation"`
	ChangeRequired             bool                 `gorm:"column:change_required;not null;default:true" json:"change_required"`
	ChangeType                 *string              `gorm:"column:change_type" json:"change_type"`
	ChangeTemplate             *string              `gorm:"column:change_template" json:"change_template"`
	ChangeStartDate            *time.Time           `gorm:"column:change_start_date" json:"change_start_date"`
	ChangeEndDate              *time.Time           `gorm:"column:change_end_date" json:"change_end_date"`
	ChangeStatus               ChangeStatus         `gorm:"column:change_status;not null;default:'new'" json:"change_status"`
	ChangeAction               *string              `gorm:"column:change_action;type:text" json:"change_action"`
	ChangeNumber               *string              `gorm:"column:change_number;type:text" json:"change_number"`
	ChangeSysId                *string              `gorm:"column:change_sys_id;type:text" json:"change_sys_id"`
	ChangeLink                 *string              `gorm:"column:change_link;type:text" json:"change_link"`
	ChangeError                *string              `gorm:"column:change_error;type:text" json:"change_error"`
	ChangeJustification        *string              `gorm:"column:change_justification;type:text" json:"change_justification"`
	ChangeImplementationPlan   *string              `gorm:"column:change_implementation_plan;type:text" json:"change_implementation_plan"`
	ChangeRiskImpactAnalysis   *string              `gorm:"column:change_risk_impact_analysis;type:text" json:"change_risk_impact_analysis"`
	ChangeBackoutPlan          *string              `gorm:"column:change_backout_plan;type:text" json:"change_backout_plan"`
	AwxJobEnabled              bool                 `gorm:"column:awx_job_enabled;not null;default:true" json:"awx_job_enabled"`
	AwxTemplateType            AwxTemplateType      `gorm:"column:awx_template_type;not null" json:"awx_template_type"`
	AwxTemplateID              *int64               `gorm:"column:awx_template_id" json:"awx_template_id"`
	AwxInventoryID             *int32               `gorm:"column:awx_inventory_id" json:"awx_inventory_id"`
	AwxCredentials             *string              `gorm:"column:awx_credentials" json:"awx_credentials"`
	AwxJobType                 *string              `gorm:"column:awx_job_type" json:"awx_job_type"`
	AwxLimit                   *string              `gorm:"column:awx_limit" json:"awx_limit"`
	AwxJobTags                 *string              `gorm:"column:awx_job_tags" json:"awx_job_tags"`
	AwxSkipTags                *string              `gorm:"column:awx_skip_tags" json:"awx_skip_tags"`
	AwxExtraVars               *string              `gorm:"column:awx_extra_vars;type:text" json:"awx_extra_vars"`
	AwxSCMBranch               *string              `gorm:"column:awx_scm_branch" json:"awx_scm_branch"`
	AwxVerbosity               *int                 `gorm:"column:awx_verbosity" json:"awx_verbosity"`
	AwxTimeout                 *int                 `gorm:"column:awx_timeout" json:"awx_timeout"`
	AwxForks                   *int                 `gorm:"column:awx_forks" json:"awx_forks"`
	AwxJobSliceCount           *int                 `gorm:"column:awx_job_slice_count" json:"awx_job_slice_count"`
	AwxExecutionEnvironment    *int                 `gorm:"column:awx_execution_environment" json:"awx_execution_environment"`
	AwxInstanceGroups          *string              `gorm:"column:awx_instance_groups" json:"awx_instance_groups"`
	AwxLabels                  *string              `gorm:"column:awx_labels" json:"awx_labels"`
	AwxEstimatedRuntime        *int                 `gorm:"column:awx_estimated_runtime" json:"awx_estimated_runtime"`
	AwxVariables               *string              `gorm:"column:awx_variables;type:text" json:"awx_variables"`
	AwxArtifacts               *string              `gorm:"column:awx_artifacts;type:text" json:"awx_artifacts"`
	AwxStatus                  AwxStatus            `gorm:"column:awx_status;not null;default:'new'" json:"awx_status"`
	AwxNextStatusCheck         *time.Time           `gorm:"column:awx_next_status_check" json:"awx_next_status_check"`
	AwxJobId                   *int64               `gorm:"column:awx_job_id" json:"awx_job_id"`
	AwxJobLink                 *string              `gorm:"column:awx_job_link;type:text" json:"awx_job_link"`
	AwxError                   *string              `gorm:"column:awx_error;type:text" json:"awx_error"`
	QuickDiscoveryStatus       QuickdiscoveryStatus `gorm:"column:quickdiscovery_status;not null;default:'new'" json:"quickdiscovery_status"`
	QuickDiscoveryError        *string              `gorm:"column:quickdiscovery_error;type:text" json:"quickdiscovery_error"`
	QuickDiscoveryCiSysid      *string              `gorm:"column:quickdiscovery_ci_sysid;type:text" json:"quickdiscovery_ci_sysid"`
	QuickDiscoveryCiName       *string              `gorm:"column:quickdiscovery_ci_name;type:text" json:"quickdiscovery_ci_name"`
	QuickDiscoveryErrorCounter int                  `gorm:"column:quickdiscovery_error_counter;not null;default:0" json:"quickdiscovery_error_counter"`
	TaggingStatus              TaggingStatus        `gorm:"column:tagging_status;not null;default:'new'" json:"tagging_status"`
	TaggingError               *string              `gorm:"column:tagging_error;type:text" json:"tagging_error"`
	Hostname                   *string              `gorm:"column:hostname;type:text" json:"hostname"`
	IP                         *string              `gorm:"column:ip;type:text" json:"ip"`
	Notification               bool                 `gorm:"column:notification;not null;default:false" json:"notification"`
	Snow                       ConfigSnow           `gorm:"foreignKey:SnowID" json:"snow"`
	Awx                        ConfigAwx            `gorm:"foreignKey:AwxID" json:"awx"`
	User                       *User                `gorm:"foreignKey:UserID" json:"user,omitempty"`
	Server                     *Server              `gorm:"foreignKey:ServerID" json:"server,omitempty"`
	Appservice                 *Appservice          `gorm:"foreignKey:AppserviceID" json:"appservice,omitempty"`
	AwxStartDate               *time.Time           `gorm:"column:awx_start_date" json:"awx_start_date"`
	AwxEndDate                 *time.Time           `gorm:"column:awx_end_date" json:"awx_end_date"`
	JobEndDate                 *time.Time           `gorm:"column:job_end_date" json:"job_end_date"`
	NonPostgres                bool                 `gorm:"column:non_postgres;not null;default:false" json:"non_postgres"`
	NonPostgresJustification   *string              `gorm:"column:non_postgres_justification;type:text" json:"non_postgres_justification"`
	NonPostgresEmailStatus     EmailStatus          `gorm:"column:non_postgres_email_status;not null;default:'new'" json:"non_postgres_email_status"`
	NonOSS                     bool                 `gorm:"column:non_oss;not null;default:false" json:"non_oss"`
	TargetDatabaseType         *string              `gorm:"column:target_database_type" json:"target_database_type"`
	IsLowPriority              bool                 `gorm:"column:is_low_priority;not null;default:false" json:"is_low_priority"`
	AwxJobName                 *string              `gorm:"column:awx_job_name" json:"awx_job_name"`
	AwxJobStatus               *string              `gorm:"column:awx_job_status" json:"awx_job_status"`
	AwxJobFailed               *bool                `gorm:"column:awx_job_failed" json:"awx_job_failed"`
	AwxJobReturnCompleted      *bool                `gorm:"column:awx_job_return_completed" json:"awx_job_return_completed"`
	AwxJobReturnMessage        *string              `gorm:"column:awx_job_return_message" json:"awx_job_return_message"`
	AwxJobReturnData           *string              `gorm:"column:awx_job_return_data" json:"awx_job_return_data"`
	AwxJobOrg                  *string              `gorm:"column:awx_job_org" json:"awx_job_org"`
	AwxJobErrorMessage         *string              `gorm:"column:awx_job_error_message" json:"awx_job_error_message"`
	AwxTemplateLink            *string              `gorm:"column:awx_template_link" json:"awx_template_link"`
	AwxTemplateName            *string              `gorm:"column:awx_template_name" json:"awx_template_name"`
	AwxDuration                *string              `gorm:"column:awx_duration;->;type:interval" json:"awx_duration"`
	JobDuration                *string              `gorm:"column:job_duration;->;type:interval" json:"job_duration"`
	AwxJobArtifacts            *string              `gorm:"column:awx_job_artifacts;type:text" json:"awx_job_artifacts"`
	CreateIncidents            bool                 `gorm:"column:create_incidents;default:true" json:"create_incidents"`
}

func (Job) TableName() string {
	return "cmp.job"
}
