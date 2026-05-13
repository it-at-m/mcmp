package db

import (
	"fmt"

	"github.com/it-at-m/mcmp/mcmp-eai-vcenter/pkg/config"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
)

func OpenPostgres(general config.General, database config.Database) (*gorm.DB, error) {
	escapedUsername, escapedPassword, gormLogger, err := ConfigDB(database.Username, general.Passphrase, database.EncryptedPassword, general.Debug)
	if err != nil {
		return nil, fmt.Errorf("Config database error: %v", err)
	}
	DSN := fmt.Sprintf("%s user=%s password=%s", database.DSN, escapedUsername, escapedPassword)
	db, err := gorm.Open(postgres.Open(DSN), &gorm.Config{Logger: gormLogger})
	if err != nil {
		return nil, fmt.Errorf("Open Postgres error: %v", err)
	}
	return db, nil
}
