package db

import "time"

type Group struct {
	ID        int64     `gorm:"column:id;primaryKey;autoIncrement" json:"id"`
	Version   int64     `gorm:"column:version;default:0" json:"version"`
	CreatedAt time.Time `gorm:"column:created_at;default:CURRENT_TIMESTAMP" json:"created_at"`
	UpdatedAt time.Time `gorm:"column:updated_at;default:CURRENT_TIMESTAMP" json:"updated_at"`
	Name      string    `gorm:"column:name;size:100;not null" json:"name"`
	SysID     string    `gorm:"column:sys_id;size:100;not null;uniqueIndex" json:"sys_id"`
}

func (Group) TableName() string {
	return "cmp.group"
}
