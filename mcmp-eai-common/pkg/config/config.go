package config

import (
	"fmt"

	"github.com/spf13/viper"
)

// LoadConfig is a generic function that loads configuration from a TOML file
// It uses Viper library to handle configuration file parsing and environment variable support
//
// Type Parameters:
//   - T: The configuration struct type that matches the TOML file structure
//
// Parameters:
//   - appname: The base name for the configuration file (without extension)
//
// Returns:
//   - *T: Pointer to the populated configuration struct
//   - error: Configuration loading or parsing error
//
// Configuration file search locations (in order):
// 1. $HOME/.{appname}/{appname}.toml
// 2. ./{appname}.toml (current directory)
//
// The function will return an error if the configuration file
// cannot be found, read, or parsed successfully.
func LoadConfig[T any](appname string) (*T, error) {
	// Set configuration file name (without extension)
	viper.SetConfigName(appname)

	// Set configuration file type to TOML format
	viper.SetConfigType("toml")

	// Add configuration file search paths
	// These paths are searched in the order they are added
	viper.AddConfigPath("$HOME/." + appname) // User-specific config in home directory
	viper.AddConfigPath(".")                 // Current working directory

	// Attempt to read the configuration file
	if err := viper.ReadInConfig(); err != nil {
		return nil, fmt.Errorf("config read error: %w", err)
	}

	// Create instance of the generic configuration type
	var cfg T

	// Unmarshal the configuration data into the struct
	// Viper automatically maps TOML keys to struct fields
	if err := viper.Unmarshal(&cfg); err != nil {
		return nil, fmt.Errorf("config unmarshal error: %w", err)
	}
	return &cfg, nil
}
