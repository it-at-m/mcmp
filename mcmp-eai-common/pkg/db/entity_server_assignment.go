package db

type ServerAssignment struct {
	ServerID     int64 `gorm:"column:server_id;primaryKey;not null" json:"server_id"`
	AppserviceID int64 `gorm:"column:appservice_id;primaryKey;not null" json:"appservice_id"`
}

func (ServerAssignment) TableName() string {
	return "cmp.server_assignment"
}
