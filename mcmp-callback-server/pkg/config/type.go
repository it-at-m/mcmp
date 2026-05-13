package config

import (
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

// Configuration structs that define the structure of the TOML configuration file
// These structs are used by Viper to unmarshal the configuration into Go structs

// General contains general application settings
type General struct {
	Debug bool
	Port  int
}

// Database settings common for all applications
type Database struct {
	DSN      string
	Username string
	Password string
}

// LogConfig contains configuration options for setting up structured logging.
// It supports console output, file output with rotation, or both simultaneously.

// Config is the root configuration structure that combines all configuration sections
// This structure mirrors the TOML configuration file format
type Config struct {
	GENERAL  General // General application settings
	DATABASE Database
	LOGGING  logging.LogConfig
}
