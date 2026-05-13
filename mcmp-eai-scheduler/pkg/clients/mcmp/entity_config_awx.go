package mcmp

type ConfigAwx struct {
	ID             int64   `gorm:"primaryKey;autoIncrement" json:"id"`
	ApiDescription *string `gorm:"size:100" json:"api_description"`
	ApiUsername    string  `gorm:"size:100;not null" json:"api_username"`
	ApiPassword    string  `gorm:"-" json:"api_password"`
	ApiEndpoint    string  `gorm:"size:500;not null;uniqueIndex:unique_config_awx_api_endpoint" json:"api_endpoint"`
	Enabled        bool    `gorm:"not null;default:false" json:"enabled"`
}

func (ConfigAwx) TableName() string {
	return "cmp.config_awx"
}
