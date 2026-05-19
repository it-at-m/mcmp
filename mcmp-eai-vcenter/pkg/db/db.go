package db

import (
	"fmt"
	"log"
	"net/url"
	"os"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-vcenter/pkg/cipher"
	"gorm.io/gorm/logger"
)

func ConfigDB(username, passphrase, encryptedPassword string, debug bool) (escapedUsername string, escapedPassword string, gormLogger logger.Interface, err error) {
	password, err := cipher.DecryptString(passphrase, encryptedPassword)
	if err != nil {
		return "", "", nil, fmt.Errorf("decrypt database password error: %w", err)
	}
	escapedUsername = url.QueryEscape(username)
	//	escapedPassword = url.QueryEscape(password)
	escapedPassword = password
	gormLogLevel := logger.Warn
	if debug {
		gormLogLevel = logger.Info
	}
	gormLogger = logger.New(
		log.New(os.Stdout, "\n", log.LstdFlags),
		logger.Config{
			SlowThreshold:             1000 * time.Millisecond,
			LogLevel:                  gormLogLevel,
			IgnoreRecordNotFoundError: true,
			Colorful:                  true,
		},
	)
	return escapedUsername, escapedPassword, gormLogger, nil
}
