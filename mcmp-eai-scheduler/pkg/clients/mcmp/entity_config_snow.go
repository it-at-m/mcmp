package mcmp

type ConfigSnow struct {
	ID               int64   `gorm:"primaryKey;autoIncrement" json:"id"`
	ApiDescription   *string `gorm:"size:100" json:"api_description"`
	ApiClientID      string  `gorm:"size:100;not null;default:''" json:"api_client_id"`
	ApiClientSecret  string  `gorm:"-" json:"api_client_secret"`
	ApiClientAuthUrl string  `gorm:"size:100;not null" json:"api_client_auth_url"`
	ApiEndpoint      string  `gorm:"size:500;not null;uniqueIndex:unique_config_snow_api_endpoint" json:"api_endpoint"`
	Enabled          bool    `gorm:"not null;default:false" json:"enabled"`
	Proxy            string  `gorm:"size:500;not null" json:"proxy"`
	UseProxy         bool    `gorm:"not null;default:false" json:"use_proxy"`
}

func (ConfigSnow) TableName() string {
	return "cmp.config_snow"
}
