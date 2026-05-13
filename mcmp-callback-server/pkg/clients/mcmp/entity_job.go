package mcmp

import "time"

// Job represents a database entity for managing job records and their associated state.
// It includes metadata fields for tracking creation, updates, and version control.
type Job struct {
	ID                     int64                `gorm:"column:id;primaryKey;autoIncrement" json:"id"`
	Version                int64                `gorm:"column:version;default:0" json:"version"`
	CreatedAt              time.Time            `gorm:"column:created_at;default:CURRENT_TIMESTAMP" json:"created_at"`
	UpdatedAt              time.Time            `gorm:"column:updated_at;default:CURRENT_TIMESTAMP" json:"updated_at"`
	Status                 JobStatus            `gorm:"column:status;not null;default:'new'" json:"status"`
	ChangeRequired         bool                 `gorm:"column:change_required;not null;default:true" json:"change_required"`
	ChangeStatus           ChangeStatus         `gorm:"column:change_status;not null;default:'new'" json:"change_status"`
	ChangeError            *string              `gorm:"column:change_error;type:text" json:"change_error"`
	QuickDiscovery         bool                 `gorm:"column:quickdiscovery;not null" json:"quickdiscovery"`
	QuickDiscoveryStatus   QuickdiscoveryStatus `gorm:"column:quickdiscovery_status;not null;default:'new'" json:"quickdiscovery_status"`
	QuickDiscoveryError    *string              `gorm:"column:quickdiscovery_error;type:text" json:"quickdiscovery_error"`
	QuickDiscoveryCiSysid  *string              `gorm:"column:quickdiscovery_ci_sysid;size:100" json:"quickdiscovery_ci_sysid"`
	QuickDiscoveryCiName   *string              `gorm:"column:quickdiscovery_ci_name;size:100" json:"quickdiscovery_ci_name"`
	ServerInstallation     bool                 `gorm:"column:server_installation;not null" json:"server_installation"`
	Title                  *string              `gorm:"column:title;size:255" json:"title"`
	Description            *string              `gorm:"column:description;type:text" json:"description"`
	ActionErrorTitle       *string              `gorm:"column:action_error_title;size:255" json:"action_error_title"`
	ActionErrorDescription *string              `gorm:"column:action_error_description;type:text" json:"action_error_description"`
}

func (Job) TableName() string {
	return "cmp.job"
}
