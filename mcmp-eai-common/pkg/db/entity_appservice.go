package db

import "time"

type Appservice struct {
	ID            int64     `gorm:"column:id;primaryKey;autoIncrement" json:"id"`
	Version       int64     `gorm:"column:version;not null;default:0" json:"version"`
	CreatedAt     time.Time `gorm:"column:created_at;default:CURRENT_TIMESTAMP" json:"created_at"`
	UpdatedAt     time.Time `gorm:"column:updated_at;default:CURRENT_TIMESTAMP" json:"updated_at"`
	Name          *string   `gorm:"column:name;size:1000" json:"name"`
	SysID         string    `gorm:"column:sys_id;size:100;not null;uniqueIndex" json:"sys_id"`
	Number        string    `gorm:"column:number;size:100;not null" json:"number"`
	ChangeGroupID *int64    `gorm:"column:change_group_id" json:"change_group_id"`
	ChangeGroup   *Group    `gorm:"foreignKey:ChangeGroupID" json:"change_group,omitempty"`
}

func (Appservice) TableName() string {
	return "cmp.appservice"
}
