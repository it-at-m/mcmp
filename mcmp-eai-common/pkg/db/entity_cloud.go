package db

type Cloud struct {
	ID int64 `gorm:"primaryKey;autoIncrement" json:"id"`
}

func (Cloud) TableName() string {
	return "cmp.cloud"
}
