package app

import (
	"bufio"
	"fmt"
	"log"
	"os"
	"strings"

	"github.com/it-at-m/mcmp/mcmp-eai-baas/pkg/cipher"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
	"gorm.io/gorm"
)

var debugLogger = logging.NewDebugLogger(log.Default())

func CryptPassword() error {
	reader := bufio.NewReader(os.Stdin)
	fmt.Print("Passphrase       : ")
	passphrase, _ := reader.ReadString('\n')
	passphrase = strings.TrimSpace(passphrase)
	fmt.Print("Password         : ")
	password, _ := reader.ReadString('\n')
	password = strings.TrimSpace(password)
	encryptedPw, err := cipher.EncryptString(passphrase, password)
	if err != nil {
		return fmt.Errorf("CryptPassword-Error: %w", err)
	}
	fmt.Printf("EncryptedPassword: %s\n", encryptedPw)
	return nil
}

func AutoMigrateTable(db *gorm.DB, models ...interface{}) error {
	stmt := &gorm.Statement{DB: db}
	for _, model := range models {
		err := stmt.Parse(model)
		if err != nil {
			return fmt.Errorf("AutoMigrate-Error: %w", err)
		}
		fmt.Printf("Create or migrate table %s.\n", stmt.Schema.Table)
		err = db.AutoMigrate(model)
		if err != nil {
			return fmt.Errorf("AutoMigrate-Error: %w", err)
		}
	}
	return nil
}
