package db

import (
	"time"
)

type JobNode struct {
	ID                 int64      `gorm:"column:id;primaryKey;autoIncrement" json:"id"`
	Version            int64      `gorm:"column:version;not null;default:0" json:"version"`
	CreatedAt          time.Time  `gorm:"column:created_at;default:CURRENT_TIMESTAMP" json:"created_at"`
	UpdatedAt          time.Time  `gorm:"column:updated_at;default:CURRENT_TIMESTAMP" json:"updated_at"`
	JobID              int64      `gorm:"column:job_id;not null" json:"job_id"`
	NodeID             int64      `gorm:"column:node_id;not null" json:"node_id"`
	NodeAlias          *string    `gorm:"column:node_alias" json:"node_alias"`
	NodeIdentifier     *string    `gorm:"column:node_identifier" json:"node_identifier"`
	ParentJobID        int64      `gorm:"column:parent_job_id;not null" json:"parent_job_id"`
	ParentJobLink      *string    `gorm:"column:parent_job_link" json:"parent_job_link"`
	TemplateID         *int64     `gorm:"column:template_id" json:"template_id"`
	TemplateLink       *string    `gorm:"column:template_link" json:"template_link"`
	TemplateName       *string    `gorm:"column:template_name" json:"template_name"`
	TemplateType       *string    `gorm:"column:template_type" json:"template_type"`
	JobAwxID           *int64     `gorm:"column:job_awx_id" json:"job_awx_id"`
	JobAwxLink         *string    `gorm:"column:job_awx_link" json:"job_awx_link"`
	JobName            *string    `gorm:"column:job_name" json:"job_name"`
	JobType            *string    `gorm:"column:job_type" json:"job_type"`
	JobStatus          *string    `gorm:"column:job_status" json:"job_status"`
	JobFailed          *bool      `gorm:"column:job_failed" json:"job_failed"`
	JobReturnCompleted *bool      `gorm:"column:job_return_completed" json:"job_return_completed"`
	JobReturnMessage   *string    `gorm:"column:job_return_message" json:"job_return_message"`
	JobReturnData      *string    `gorm:"column:job_return_data" json:"job_return_data"`
	JobOrg             *string    `gorm:"column:job_org" json:"job_org"`
	JobStarted         *time.Time `gorm:"column:job_started" json:"job_started"`
	JobFinished        *time.Time `gorm:"column:job_finished" json:"job_finished"`
	JobDuration        *string    `gorm:"column:job_duration;->;type:interval" json:"job_duration"` // -> marks it as read-only (generated)
	JobDepth           *int       `gorm:"column:job_depth" json:"job_depth"`
	JobErrorMessage    *string    `gorm:"column:job_error_message" json:"job_error_message"`
	JobExtraVars       *string    `gorm:"column:job_extra_vars;type:text" json:"job_extra_vars"`
	SuccessNodes       []int      `gorm:"-" json:"success_nodes"`
	FailureNodes       []int      `gorm:"-" json:"failure_nodes"`
	AlwaysNodes        []int      `gorm:"-" json:"always_nodes"`
	JobIsRootCause     bool       `gorm:"column:job_is_root_cause;default:false" json:"job_is_root_cause"`
	JobArtifacts       *string    `gorm:"column:job_artifacts;type:text" json:"job_artifacts"`
}

func (JobNode) TableName() string {
	return "cmp.job_nodes"
}
