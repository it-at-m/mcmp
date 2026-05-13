package db

import (
	"fmt"
	"log"

	"github.com/it-at-m/mcmp/mcmp-eai-baas/pkg/config"
	"gorm.io/driver/mysql"
	"gorm.io/gorm"
)

func OpenMySQL(general config.General, database config.Database) *gorm.DB {
	escapedUsername, escapedPassword, gormLogger := ConfigDB(database.Username, general.Passphrase, database.EncryptedPassword, general.Debug)
	DSN := fmt.Sprintf("%s:%s@%s", escapedUsername, escapedPassword, database.DSN)
	db, err := gorm.Open(mysql.Open(DSN), &gorm.Config{Logger: gormLogger})
	if err != nil {
		log.Fatalf("Open MySQL error: %v", err)
	}
	return db
}
