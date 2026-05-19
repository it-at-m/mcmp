package db

import "time"

type Server struct {
	ID                         int64      `gorm:"primaryKey;autoIncrement" json:"id"`
	Version                    int64      `gorm:"not null;default:0" json:"version"`
	CreatedAt                  time.Time  `gorm:"default:CURRENT_TIMESTAMP" json:"created_at"`
	UpdatedAt                  time.Time  `gorm:"default:CURRENT_TIMESTAMP" json:"updated_at"`
	CloudID                    int64      `gorm:"not null;constraint:fk_server_cloud,OnDelete:CASCADE" json:"cloud_id"`
	UUID                       string     `gorm:"size:50;not null" json:"uuid"`
	InstanceUUID               *string    `gorm:"size:50" json:"instance_uuid"`
	VMID                       *string    `gorm:"size:50" json:"vm_id"`
	Name                       string     `gorm:"size:200;not null" json:"name"`
	FQDN                       *string    `gorm:"size:100" json:"fqdn"`
	SnowServerSysID            *string    `gorm:"size:100" json:"snow_server_sys_id"`
	SnowServerSysClass         *string    `gorm:"size:50" json:"snow_server_sys_class"`
	SnowServerHardwareStatus   *string    `gorm:"size:50" json:"snow_server_hardware_status"`
	SnowServerLastDiscovered   *time.Time `json:"snow_server_last_discovered"`
	SnowInstanceSysID          *string    `gorm:"size:100" json:"snow_instance_sys_id"`
	SnowInstanceSysClass       *string    `gorm:"size:50" json:"snow_instance_sys_class"`
	SnowInstanceLastDiscovered *time.Time `json:"snow_instance_last_discovered"`

	Cloud Cloud `gorm:"foreignKey:CloudID" json:"cloud"`
}

func (Server) TableName() string {
	return "cmp.server"
}
