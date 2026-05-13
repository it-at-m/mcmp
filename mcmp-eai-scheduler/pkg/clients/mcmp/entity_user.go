package mcmp

import "time"

type User struct {
	ID        int64     `gorm:"primaryKey;autoIncrement" json:"id"`
	Version   int64     `gorm:"not null;default:0" json:"version"`
	CreatedAt time.Time `gorm:"default:CURRENT_TIMESTAMP" json:"created_at"`
	UpdatedAt time.Time `gorm:"default:CURRENT_TIMESTAMP" json:"updated_at"`
	Username  string    `gorm:"size:100;not null;uniqueIndex" json:"username"`
	SysID     string    `gorm:"size:100;not null;uniqueIndex" json:"sys_id"`
}

func (User) TableName() string {
	return "cmp.user"
}
