package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"log/slog"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-patchnight/pkg/clients/mcmp"
	"github.com/it-at-m/mcmp/mcmp-eai-patchnight/pkg/clients/patchnight"
	"github.com/it-at-m/mcmp/mcmp-eai-patchnight/pkg/processor"
	"github.com/spf13/viper"
)

// Global debug flag that controls verbose logging throughout the application
var (
	debug  = false
	logger *slog.Logger
)

// Application name constant used for configuration file naming and identification
const (
	appName = "mcmp-eai-patchnight"
)

// Configuration structs that define the structure of the TOML configuration file
// These structs are used by Viper to unmarshal the configuration into Go structs

// GeneralConfig contains general application settings
type GeneralConfig struct {
	Debug bool // Enables debug logging when set to true
}

// PatchnightConfig contains connection settings for Patchnight instance
type PatchnightConfig struct {
	Hostname string // Patchnight instance hostname (e.g., "patchnight.example.com")
}

// McmpConfig contains settings for the MCMP (Multi Cloud Management Platform) API
type McmpConfig struct {
	OAuthUrl          string // Keycloak authentication server base URL
	OAuthRealm        string // Keycloak realm name where the client is configured
	OAuthClientId     string // OAuth 2.0 client identifier
	OAuthClientSecret string // OAuth 2.0 client secret for authentication
	ApiEndpoint       string // MCMP API endpoint URL where data will be posted
}

// Config is the root configuration structure that combines all configuration sections
// This structure mirrors the TOML configuration file format
type Config struct {
	General    GeneralConfig    // General application settings
	Patchnight PatchnightConfig // Patchnight connection configuration
	MCMP       McmpConfig       // MCMP API configuration
}

// init function initializes the structured logging with slog (Go 1.21+)
// This function is called automatically when the package is imported
func init() {
	// Initialize structured logging with slog (Go 1.21+)
	logger = slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	}))
}

// main is the entry point of the application
// It handles command line argument parsing and delegates to the run function
// The application expects no command line arguments and will show usage if any are provided
func main() {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// Signal handler for graceful shutdown
	signalChan := make(chan os.Signal, 1)
	signal.Notify(signalChan, os.Interrupt, syscall.SIGTERM)

	go func() {
		<-signalChan
		logger.Info("Received shutdown signal, gracefully shutting down...")
		cancel()
	}()

	if err := runApp(ctx); err != nil {
		if errors.Is(err, context.Canceled) {
			logger.Info("Application stopped by user")
			return
		}
		logger.Error("Application error", "error", err)
		os.Exit(1)
	}
}

// runApp handles command line argument parsing and validation
// It ensures the application is called with correct arguments before proceeding
//
// Parameters:
//   - ctx: Context for cancellation and timeout handling
//
// Returns:
//   - error: Command line parsing error or execution error
func runApp(ctx context.Context) error {
	flag.Usage = func() {
		fmt.Fprintf(flag.CommandLine.Output(), "Usage: %s\n", os.Args[0])
		flag.PrintDefaults()
	}

	flag.Parse()

	if len(os.Args) != 1 {
		return fmt.Errorf("wrong number of arguments")
	}

	return run(ctx)
}

// run executes the main application logic
// This function orchestrates the entire data synchronization process:
// 1. Loads configuration from TOML file
// 2. Creates Patchnight client and processes application services
// 3. Retrieves and exports Patchnight data
// 4. Authenticates with Keycloak to get access token
// 5. Sends processed data to MCMP API
//
// Parameters:
//   - ctx: Context for cancellation and timeout handling
//
// Returns:
//   - error: Any error that prevents successful execution
func run(ctx context.Context) error {
	// Timeout for the entire operation
	ctx, cancel := context.WithTimeout(ctx, 5*time.Minute)
	defer cancel()

	cfg, err := loadConfig[Config](appName)
	if err != nil {
		return fmt.Errorf("failed to load config: %w", err)
	}
	debug = cfg.General.Debug

	// Update logger level based on debug flag
	updateLoggerLevel()

	// Configuration validation
	if err := cfg.validate(); err != nil {
		return fmt.Errorf("invalid configuration: %w", err)
	}

	// Create Patchnight client with configuration parameters
	// The client handles HTTP communication with Patchnight REST APIs
	patchnightConfig := patchnight.ClientConfig{
		Hostname:        cfg.Patchnight.Hostname,
		Debug:           debug,
		EnableTLSVerify: true, // Security improvement: Enable TLS verification
		RequestTimeout:  30 * time.Second,
		MaxRetries:      3,
		RetryDelay:      1 * time.Second,
		UserAgent:       "MCMP-EAI-PatchnightConfig/1.0",
	}

	patchnightClient, err := patchnight.NewClient(patchnightConfig)
	if err != nil {
		return fmt.Errorf("failed to create patchnight client: %v", err)
	}

	// Create and configure ServiceProcessor for handling Patchnight data operations
	// The processor encapsulates the business logic for data retrieval and transformation
	serviceProcessor := processor.NewServiceProcessor(patchnightClient, debug)

	// Process patchnight data from Patchnight
	// This involves fetching app services and their related data (CIs, groups, users)
	if err := serviceProcessor.ProcessPatchnightData(); err != nil {
		return fmt.Errorf("error processing patchnight data: %v", err)
	}

	// Export Patchnight data as JSON string for API transmission
	// The JSON format is required by the MCMP API for data ingestion
	jsonData, err := serviceProcessor.ExportToJSON()
	if err != nil {
		return fmt.Errorf("error during JSON export: %v", err)
	}
	logDebugf("JSON export successful, length: %d characters", len(jsonData))

	// Export Patchnight data to a local file for backup/debugging purposes
	// This creates a persistent copy of the data that was sent to MCMP
	err = serviceProcessor.ExportToFile("patchnight_export.json")
	if err != nil {
		return fmt.Errorf("error during file export: %v", err)
	}

	// Create MCMP client for API communication
	// The client handles HTTP communication with the MCMP platform
	mcmpConfig := mcmp.ClientConfig{
		Debug:           debug,
		AuthServerURL:   cfg.MCMP.OAuthUrl,
		Realm:           cfg.MCMP.OAuthRealm,
		ClientID:        cfg.MCMP.OAuthClientId,
		ClientSecret:    cfg.MCMP.OAuthClientSecret,
		EnableTLSVerify: true, // Backward compatibility
		RequestTimeout:  30 * time.Second,
		Scopes:          []string{},
	}
	mcmpClient, err := mcmp.NewClient(mcmpConfig)
	if err != nil {
		return fmt.Errorf("failed to create MCMP client: %v", err)
	}

	// Send processed Patchnight data to MCMP API endpoint
	// This completes the data synchronization process
	if err := mcmpClient.SendPatchnightData(ctx, cfg.MCMP.ApiEndpoint, []byte(jsonData)); err != nil {
		return fmt.Errorf("error sending data to MCMP: %v", err)
	}
	logDebugf("Data successfully sent to MCMP")
	return nil
}

// loadConfig is a generic function that loads configuration from a TOML file
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
func loadConfig[T any](appname string) (*T, error) {
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

// validate validates the configuration struct to ensure all required fields are present
// This method performs comprehensive validation of all configuration sections
//
// Returns:
//   - error: Validation error if any required field is missing or invalid
func (c *Config) validate() error {
	if c.Patchnight.Hostname == "" {
		return fmt.Errorf("patchnight hostname is required")
	}
	if c.MCMP.OAuthUrl == "" {
		return fmt.Errorf("MCMP OAuth URL is required")
	}
	if c.MCMP.OAuthRealm == "" {
		return fmt.Errorf("MCMP OAuth realm is required")
	}
	if c.MCMP.OAuthClientId == "" {
		return fmt.Errorf("MCMP OAuth client ID is required")
	}
	if c.MCMP.OAuthClientSecret == "" {
		return fmt.Errorf("MCMP OAuth client secret is required")
	}
	if c.MCMP.ApiEndpoint == "" {
		return fmt.Errorf("MCMP API endpoint is required")
	}
	return nil
}

// logDebugf provides conditional debug logging functionality
// It only outputs log messages when the global debug flag is enabled
// This allows for verbose logging during development and troubleshooting
// without cluttering production logs
//
// Parameters:
//   - format: Printf-style format string
//   - a: Variable arguments for format string placeholders
//
// The function uses the structured logger with debug level
// Debug messages are sent to the configured logger output
func logDebugf(msg string, args ...interface{}) {
	if debug {
		logger.Debug(fmt.Sprintf(msg, args...))
	}
}

// logErrorf provides error logging functionality using structured logging
// This function formats error messages and logs them at error level
//
// Parameters:
//   - format: Printf-style format string
//   - a: Variable arguments for format string placeholders
//
// The function uses the structured logger with error level
// Error messages are sent to the configured logger output
func logErrorf(msg string, args ...interface{}) {
	logger.Error(fmt.Sprintf(msg, args...))
}

// updateLoggerLevel updates the logger level based on the debug flag
// This function should be called after the configuration is loaded
func updateLoggerLevel() {
	var level slog.Level
	if debug {
		level = slog.LevelDebug
	} else {
		level = slog.LevelInfo
	}
	logger = slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{
		Level: level,
	}))
}
