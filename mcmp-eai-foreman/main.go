package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/app"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/client/mcmp"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/config"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
	"github.com/it-at-m/mcmp/mcmp-eai-foreman/pkg/clients/foreman"
	"github.com/it-at-m/mcmp/mcmp-eai-foreman/pkg/processor"
)

// Global debug flag that controls verbose logging throughout the application
var (
	logger                    *logging.StructuredLogger
	ErrWrongNumberOfArguments = errors.New("wrong number of arguments")
)

// Application name constant used for configuration file naming and identification
const (
	appName            = "mcmp-eai-foreman"
	defaultTimeout     = 25 * time.Minute
	httpRequestTimeout = 60 * time.Second
	maxRetries         = 5
	retryDelay         = 3 * time.Second
	signalBufferSize   = 1
)

// Configuration structs that define the structure of the TOML configuration file
// These structs are used by Viper to unmarshal the configuration into Go structs

// GeneralConfig contains general application settings
type GeneralConfig struct {
	Debug bool // Enables debug logging when set to true
}

// ForemanConfig  contains connection settings for Foreman instance
type ForemanConfig struct {
	Username        string
	Password        string
	ApiEndpoint     string
	ParallelQueries int
	EnableTLSVerify *bool
}

// Config is the root configuration structure that combines all configuration sections
// This structure mirrors the TOML configuration file format
type Config struct {
	GENERAL GeneralConfig // General application settings
	LOGGING logging.LogConfig
	FOREMAN []ForemanConfig // Foreman connection configuration
	MCMP    mcmp.Config     // MCMP API configuration
}

// main is the entry point of the application
// It handles command line argument parsing and delegates to the run function
// The application expects no command line arguments and will show usage if any are provided
func main() {
	app.Bootstrap(func(ctx context.Context) error {
		// Parse command line flags
		flag.Usage = func() {
			exePath, err := os.Executable()
			if err != nil {
				_, _ = fmt.Fprintf(os.Stderr, "failed to get executable path: %v\n", err)
			}
			_, _ = fmt.Fprintf(flag.CommandLine.Output(), "Usage: %s\n", filepath.Base(exePath))
			flag.PrintDefaults()
		}
		flag.Parse()

		if len(os.Args) != 1 {
			return ErrWrongNumberOfArguments
		}

		// Execute main application logic
		return run(ctx)
	})
}

// run executes the main orchestration logic for synchronizing data between Foreman and MCMP platforms.
// It manages configuration loading, client initialization, data processing, JSON export, file backup, and data transmission.
// The function uses a 5-minute context timeout to ensure SLA compliance and includes comprehensive error handling at each step.
// Returns an error if any operation in the data synchronization pipeline fails.
func run(ctx context.Context) error {
	cfg, err := config.LoadConfig[Config](appName)
	if err != nil {
		return fmt.Errorf("failed to load config: %w", err)
	}

	// Establish timeout boundary for the complete operation to ensure SLA compliance
	// The 5-minute timeout provides sufficient time for large host inventories while preventing indefinite hangs
	ctx, cancel := context.WithTimeout(ctx, defaultTimeout)
	defer cancel()

	// Initialize Logger using the centralized setup from common
	logger, err := logging.SetupGlobalLogger(cfg.LOGGING)
	if err != nil {
		return fmt.Errorf("failed to initialize logger: %w", err)
	}

	// Configuration validation
	if validationErr := cfg.validate(); validationErr != nil {
		return fmt.Errorf("invalid configuration: %w", validationErr)
	}

	foremanClients, err := createForemanClients(cfg)
	if err != nil {
		return fmt.Errorf("failed to create Foreman client: %w", err)
	}

	// Initialize the service processor that orchestrates the complete data processing pipeline
	// The processor encapsulates business logic for host data retrieval, transformation, and validation
	serviceProcessor := processor.NewServiceProcessor(foremanClients, cfg.GENERAL.Debug)

	// Execute the complete host data processing pipeline with comprehensive error handling
	// This includes: host retrieval, detailed data fetching, transformation, and internal storage
	err = serviceProcessor.ProcessForemanHosts(ctx)
	if err != nil {
		return fmt.Errorf("error processing Foreman data: %v", err)
	}

	// Generate JSON representation of processed data for API transmission
	// The JSON format conforms to MCMP API specifications with proper structure validation
	jsonData, err := serviceProcessor.ExportForemanDataAsJSON(ctx)
	if err != nil {
		return fmt.Errorf("error during JSON export: %v", err)
	}
	logger.DebugPrintf("JSON export successful, length: %d characters", len(jsonData))

	// Create persistent backup file for audit trail and debugging purposes
	// The local file serves as a record of data transmitted to MCMP for troubleshooting
	err = serviceProcessor.ExportForemanDataToFile(ctx, "foreman_export.json")
	if err != nil {
		return fmt.Errorf("error during file export: %v", err)
	}

	// Initialize MCMP Client analog to other EAI services
	mcmpConfig := cfg.MCMP.ToClientConfig()
	mcmpConfig.RequestTimeout = httpRequestTimeout
	if cfg.MCMP.RequestTimeoutSeconds > 0 {
		mcmpConfig.RequestTimeout = time.Duration(cfg.MCMP.RequestTimeoutSeconds) * time.Second
	}

	mcmpClient, err := mcmp.NewClient(ctx, mcmpConfig, logger)
	if err != nil {
		return fmt.Errorf("failed to create MCMP client: %w", err)
	}

	// Transmit processed host data to MCMP API endpoint using secure HTTP POST
	// This completes the data synchronization pipeline with comprehensive error handling
	if err := mcmpClient.SendJSON(ctx, cfg.MCMP.ApiEndpoint, []byte(jsonData)); err != nil {
		return fmt.Errorf("error sending data to MCMP: %w", err)
	}
	logger.DebugPrintf("Data successfully sent to MCMP")
	return nil
}

// createForemanClients initializes and returns a list of configured Foreman API clients or an error if initialization fails.
// It securely configures the clients with authentication, TLS verification, retry logic, and performance settings.
func createForemanClients(cfg *Config) ([]*foreman.Client, error) {
	var clients []*foreman.Client

	for _, fc := range cfg.FOREMAN {
		enableTLS := true
		if fc.EnableTLSVerify != nil {
			enableTLS = *fc.EnableTLSVerify
		}

		// Initialize Foreman API client with comprehensive security and performance configuration
		// The client configuration includes authentication, TLS security, retry logic, and performance tuning
		foremanConfig := foreman.ClientConfig{
			Debug:           cfg.GENERAL.Debug,      // Enable debug logging for API communication
			Username:        fc.Username,            // Basic authentication username
			Password:        fc.Password,            // Basic authentication password
			ApiEndpoint:     fc.ApiEndpoint,         // Base URL for Foreman API endpoints
			ParallelQueries: fc.ParallelQueries,     // Concurrent query limit for optimal performance
			EnableTLSVerify: enableTLS,              // Enforce TLS certificate validation for security
			RequestTimeout:  httpRequestTimeout,     // HTTP request timeout for reliability
			MaxRetries:      maxRetries,             // Maximum retry attempts for transient failures
			RetryDelay:      retryDelay,             // Base delay between retry attempts
			UserAgent:       "MCMP-EAI-Foreman/1.0", // Client identification for server-side logging
		}

		// Create Foreman client instance with error handling for connection validation
		// The client encapsulates all HTTP communication logic and authentication handling
		client, err := foreman.NewClient(foremanConfig)
		if err != nil {
			return nil, fmt.Errorf("failed to create Foreman client for endpoint %s: %w", fc.ApiEndpoint, err)
		}
		clients = append(clients, client)
	}

	if len(clients) == 0 {
		return nil, fmt.Errorf("no Foreman clients configured")
	}

	return clients, nil
}

// validate validates the configuration fields and returns an error with all validation failures if any are found.
func (c *Config) validate() error {
	var validationErrors []string

	if len(c.FOREMAN) == 0 {
		validationErrors = append(validationErrors, "At least one Foreman configuration is required")
	}

	for i, f := range c.FOREMAN {
		if f.Username == "" {
			validationErrors = append(validationErrors, fmt.Sprintf("Foreman username is required (index %d)", i))
		}
		if f.Password == "" {
			validationErrors = append(validationErrors, fmt.Sprintf("Foreman password is required (index %d)", i))
		}
		if f.ApiEndpoint == "" {
			validationErrors = append(validationErrors, fmt.Sprintf("Foreman API Endpoint is required (index %d)", i))
		}
	}
	if c.MCMP.OAuthUrl == "" {
		validationErrors = append(validationErrors, "MCMP OAuth URL is required")
	}
	if c.MCMP.OAuthRealm == "" {
		validationErrors = append(validationErrors, "MCMP OAuth realm is required")
	}
	if c.MCMP.OAuthClientId == "" {
		validationErrors = append(validationErrors, "MCMP OAuth client ID is required")
	}
	if c.MCMP.OAuthClientSecret == "" {
		validationErrors = append(validationErrors, "MCMP OAuth client secret is required")
	}
	if c.MCMP.ApiEndpoint == "" {
		validationErrors = append(validationErrors, "MCMP API endpoint is required")
	}

	if len(validationErrors) > 0 {
		return fmt.Errorf("validation failed: %s", strings.Join(validationErrors, "; "))
	}

	return nil
}

func (c *Config) String() string {
	return fmt.Sprintf("Config{Debug: %v, Foreman Configs: %d, MCMP: %s}",
		c.GENERAL.Debug,
		len(c.FOREMAN),
		redactSensitive(c.MCMP.OAuthClientId))
}

func redactSensitive(value string) string {
	if len(value) <= 4 {
		return "***"
	}
	return value[:2] + "***" + value[len(value)-2:]
}
