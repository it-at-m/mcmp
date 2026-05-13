package db

import (
	"fmt"
	"log"

	"github.com/it-at-m/mcmp/mcmp-eai-baas/pkg/config"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
)

func OpenPostgres(general config.General, database config.Database) *gorm.DB {
	escapedUsername, escapedPassword, gormLogger := ConfigDB(database.Username, general.Passphrase, database.EncryptedPassword, general.Debug)
	DSN := fmt.Sprintf("%s user=%s password=%s", database.DSN, escapedUsername, escapedPassword)
	db, err := gorm.Open(postgres.Open(DSN), &gorm.Config{Logger: gormLogger})
	if err != nil {
		log.Fatalf("Open Postgres error: %v", err)
	}
	return db
}
