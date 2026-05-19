package db

import (
	"time"
)

// JobIncident represents a database entity for managing ServiceNow incident records
// associated with a job. It mirrors the cmp.job_incident table.
type JobIncident struct {
	ID                   int64          `gorm:"column:id;primaryKey;autoIncrement" json:"id"`
	Version              int64          `gorm:"column:version;default:0" json:"version"`
	CreatedAt            time.Time      `gorm:"column:created_at;default:CURRENT_TIMESTAMP" json:"created_at"`
	UpdatedAt            time.Time      `gorm:"column:updated_at;default:CURRENT_TIMESTAMP" json:"updated_at"`
	JobID                int64          `gorm:"column:job_id;not null" json:"job_id"`
	Status               IncidentStatus `gorm:"column:status;default:'open'" json:"status"`
	SourceType           string         `gorm:"column:source_type;not null" json:"source_type"`
	ShortDescription     *string        `gorm:"column:short_description;type:text" json:"short_description"`
	Description          *string        `gorm:"column:description;type:text" json:"description"`
	CallerSysID          *string        `gorm:"column:caller_sys_id;type:text" json:"caller_sys_id"`
	CmdbCiSysID          *string        `gorm:"column:cmdb_ci_sys_id;type:text" json:"cmdb_ci_sys_id"`
	AssignmentGroupSysID *string        `gorm:"column:assignment_group_sys_id;type:text" json:"assignment_group_sys_id"`
	AssignmentGroupName  *string        `gorm:"column:assignment_group_name;type:text" json:"assignment_group_name"`
	ChangeSysID          *string        `gorm:"column:change_sys_id;type:text" json:"change_sys_id"`
	IncidentSysID        string         `gorm:"column:incident_sys_id;not null;type:text" json:"incident_sys_id"`
	IncidentNumber       *string        `gorm:"column:incident_number;type:text" json:"incident_number"`
	IncidentLink         *string        `gorm:"column:incident_link;type:text" json:"incident_link"`
	Success              *bool          `gorm:"column:success" json:"success"`
	ErrorMessage         *string        `gorm:"column:error_message;type:text" json:"error_message"`
	CloseCodeLabel       *string        `gorm:"column:close_code_label;type:text" json:"close_code_label"`
	CloseCodeValue       *string        `gorm:"column:close_code_value;type:text" json:"close_code_value"`
	ResolvedAt           *time.Time     `gorm:"column:resolved_at" json:"resolved_at"`
	StateLabel           *string        `gorm:"column:state_label;type:text" json:"state_label"`
	StateValue           *string        `gorm:"column:state_value;type:text" json:"state_value"`
	CloseNotes           *string        `gorm:"column:close_notes;type:text" json:"close_notes"`
}

func (JobIncident) TableName() string {
	return "cmp.job_incident"
}
