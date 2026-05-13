package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"log/slog"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-foreman/pkg/clients/foreman"
	"github.com/it-at-m/mcmp/mcmp-eai-foreman/pkg/clients/mcmp"
	"github.com/it-at-m/mcmp/mcmp-eai-foreman/pkg/processor"
	"github.com/spf13/viper"
)

// Global debug flag that controls verbose logging throughout the application
var (
	debug  = false
	logger *slog.Logger
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

// McmpConfig contains settings for the MCMP (Multi Cloud Management Platform) API
type McmpConfig struct {
	OAuthUrl              string // Keycloak authentication server base URL
	OAuthRealm            string // Keycloak realm name where the client is configured
	OAuthClientId         string // OAuth 2.0 client identifier
	OAuthClientSecret     string // OAuth 2.0 client secret for authentication
	ApiEndpoint           string // MCMP API endpoint URL where data will be posted
	RequestTimeoutSeconds int    // Optional: overrides HTTP request timeout (seconds)
}

// Config is the root configuration structure that combines all configuration sections
// This structure mirrors the TOML configuration file format
type Config struct {
	GENERAL GeneralConfig   // General application settings
	FOREMAN []ForemanConfig // Foreman connection configuration
	MCMP    McmpConfig      // MCMP API configuration
}

// init function initializes the structured logging with slog (Go 1.21+)
// This function is called automatically when the package is imported
func init() {
	// Initialize structured logging with slog (Go 1.21+)
	// Creates a new JSON-formatted logger that outputs to standard output
	// This configuration is optimized for container deployments and cloud environments
	// where structured logging enables better log aggregation and analysis
	logger = slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{
		Level: slog.LevelInfo, // Set minimum log level to INFO for production-appropriate verbosity
	}))
}

// main is the entry point of the application
// It handles command line argument parsing and delegates to the run function
// The application expects no command line arguments and will show usage if any are provided
func main() {
	// Initialize cancellable context for coordinated application lifecycle management
	// This context serves as the foundation for graceful shutdown and resource cleanup
	// throughout the entire application stack and all concurrent operations
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel() // Ensure context cancellation even if main function exits early

	// Create buffered signal channel to prevent signal loss during processing
	// Buffer size of 1 ensures that shutdown signals are captured reliably
	// even if signal processing goroutine is temporarily busy with other operations
	signalChannel := make(chan os.Signal, signalBufferSize)

	// Register signal handlers for graceful shutdown in different deployment scenarios
	// os.Interrupt: Handles Ctrl+C interruption in interactive terminal sessions
	// syscall.SIGTERM: Handles termination requests from process managers, containers, and systemd
	signal.Notify(signalChannel, os.Interrupt, syscall.SIGTERM)

	// Launch concurrent signal monitoring goroutine for non-blocking shutdown handling
	// This pattern ensures that signal processing doesn't interfere with main application logic
	// while providing immediate response to shutdown requests from users or systems
	go func() {
		// Block until any registered signal is received from the operating system
		// This blocking operation runs in its own goroutine to prevent main thread blocking
		<-signalChannel

		// Log shutdown initiation for operational monitoring and audit trails
		// Structured logging provides context for troubleshooting and compliance
		logger.Info("Received shutdown signal, gracefully shutting down...")

		// Trigger application-wide shutdown by cancelling the root context
		// This propagates the shutdown signal to all context-aware operations
		// throughout the application, enabling coordinated resource cleanup
		cancel()
	}()

	// Execute main application logic with comprehensive error handling and classification
	// The context parameter enables graceful shutdown coordination and timeout management
	// across all application components including API clients and data processing
	if err := runApp(ctx); err != nil {
		// Classify error types to determine appropriate response and exit behavior
		// This enables proper process monitoring and automated restart policies
		if errors.Is(err, context.Canceled) {
			// Context cancellation indicates normal shutdown procedure (user or system initiated)
			// This is expected behavior during graceful shutdown and should not trigger alerts
			logger.Info("Application stopped by user")
			return // Exit with status code 0 indicating successful termination
		}

		// Application logic errors indicate problems requiring attention and potential alerts
		// These errors include configuration issues, network problems, authentication failures
		logger.Error("Application error", "error", err)

		// Exit with non-zero status code to indicate error condition to process managers
		// This enables automated monitoring, alerting, and restart policies in production
		os.Exit(1)
	}

	// Successful completion: Application executed without errors and shutdown normally
	// No explicit exit needed - main function return results in status code 0
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
	// Configure custom usage display function for improved user experience
	// This provides clear, formatted help information when users need guidance
	// The custom usage function displays the proper command format and available options
	flag.Usage = func() {
		// Write usage information to the standard command line output stream
		// Uses the program name from os.Args[0] to show the actual executable name
		// This ensures accuracy regardless of how the binary is named or symlinked
		fmt.Fprintf(flag.CommandLine.Output(), "Usage: %s\n", os.Args[0])

		// Display all registered command line flags and their documentation
		// Even though this application doesn't use flags, this maintains consistency
		// with Go command line application conventions and supports future extensions
		flag.PrintDefaults()
	}

	// Parse command line arguments and populate flag variables
	// This processes all command line arguments according to registered flag definitions
	// Must be called before accessing any flag values or os.Args validation
	flag.Parse()

	// Enforce strict argument count validation for security and usability
	// The application expects exactly one argument: the program name itself (os.Args[0])
	// Any additional arguments indicate incorrect usage and potential security risks
	if len(os.Args) != 1 {
		// Return descriptive error that guides users toward correct usage
		// This error is caught by the main function and displayed to the user
		// The error message is intentionally generic to avoid information disclosure
		return fmt.Errorf("wrong number of arguments")
	}

	// Delegate to the main application logic with proper context propagation
	// This separation enables clean testing and maintains single responsibility principle
	// The context is passed through to enable cancellation and timeout management
	// throughout the entire application execution pipeline
	return run(ctx)
}

// run executes the main orchestration logic for synchronizing data between Foreman and MCMP platforms.
// It manages configuration loading, client initialization, data processing, JSON export, file backup, and data transmission.
// The function uses a 5-minute context timeout to ensure SLA compliance and includes comprehensive error handling at each step.
// Returns an error if any operation in the data synchronization pipeline fails.
func run(ctx context.Context) error {
	// Establish timeout boundary for the complete operation to ensure SLA compliance
	// The 5-minute timeout provides sufficient time for large host inventories while preventing indefinite hangs
	ctx, cancel := context.WithTimeout(ctx, defaultTimeout)
	defer cancel()

	cfg, err := loadAndValidateConfig()
	if err != nil {
		return fmt.Errorf("configuration error: %w", err)
	}

	foremanClients, err := createForemanClients(cfg)
	if err != nil {
		return fmt.Errorf("failed to create Foreman client: %w", err)
	}

	// Initialize the service processor that orchestrates the complete data processing pipeline
	// The processor encapsulates business logic for host data retrieval, transformation, and validation
	serviceProcessor := processor.NewServiceProcessor(foremanClients, debug)

	// Execute the complete host data processing pipeline with comprehensive error handling
	// This includes: host retrieval, detailed data fetching, transformation, and internal storage
	err = serviceProcessor.ProcessForemanHosts(ctx)
	if err != nil {
		return fmt.Errorf("Error processing Foreman data: %v", err)
	}

	// Generate JSON representation of processed data for API transmission
	// The JSON format conforms to MCMP API specifications with proper structure validation
	jsonData, err := serviceProcessor.ExportForemanDataAsJSON(ctx)
	if err != nil {
		return fmt.Errorf("Error during JSON export: %v", err)
	}
	logDebugf("JSON export successful, length: %d characters", len(jsonData))

	// Create persistent backup file for audit trail and debugging purposes
	// The local file serves as a record of data transmitted to MCMP for troubleshooting
	err = serviceProcessor.ExportForemanDataToFile(ctx, "foreman_export.json")
	if err != nil {
		return fmt.Errorf("Error during file export: %v", err)
	}

	mcmpClient, err := createMCMPClient(cfg)
	if err != nil {
		return fmt.Errorf("failed to create MCMP client: %w", err)
	}
	mcmpClient.EnableDebug()
	// Transmit processed host data to MCMP API endpoint using secure HTTP POST
	// This completes the data synchronization pipeline with comprehensive error handling
	if err := mcmpClient.SendForemanData(ctx, cfg.MCMP.ApiEndpoint, []byte(jsonData)); err != nil {
		return fmt.Errorf("Error sending data to MCMP: %w", err)
	}
	logDebugf("Data successfully sent to MCMP")
	return nil
}

// loadAndValidateConfig loads and validates the application configuration from TOML files.
// Returns a configuration struct and error if configuration loading or validation fails.
func loadAndValidateConfig() (*Config, error) {
	// Load and validate application configuration from TOML files
	// Uses generic configuration reader for type-safe configuration loading with validation
	cfg, err := loadConfig[Config](appName)
	if err != nil {
		return nil, err
	}
	debug = cfg.GENERAL.Debug

	// Configure logging subsystem based on debug configuration setting
	// Enables detailed debug output for troubleshooting in development and staging environments
	updateLoggerLevel()

	// Perform comprehensive configuration validation before proceeding with operations
	// Early validation prevents runtime failures and provides clear error messaging
	if err := cfg.validate(); err != nil {
		return nil, err
	}

	return cfg, nil
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
			Debug:           debug,                  // Enable debug logging for API communication
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

// createMCMPClient initializes and configures an MCMP client with OAuth2 authentication and secure communication.
// It utilizes the provided configuration to establish settings such as OAuth2 parameters, TLS verification, and timeouts.
// Returns an instance of the MCMP client or an error if configuration or client creation fails.
func createMCMPClient(cfg *Config) (*mcmp.Client, error) {
	// Determine request timeout (config override if provided)
	reqTimeout := httpRequestTimeout
	if cfg.MCMP.RequestTimeoutSeconds > 0 {
		reqTimeout = time.Duration(cfg.MCMP.RequestTimeoutSeconds) * time.Second
	}

	// Configure MCMP client with OAuth2 authentication and secure communication settings
	mcmpConfig := mcmp.ClientConfig{
		Debug:           debug,                      // Enable debug logging for OAuth2 and API communication
		AuthServerURL:   cfg.MCMP.OAuthUrl,          // OAuth2 authorization server endpoint
		Realm:           cfg.MCMP.OAuthRealm,        // Authentication realm identifier
		ClientID:        cfg.MCMP.OAuthClientId,     // OAuth2 client identifier
		ClientSecret:    cfg.MCMP.OAuthClientSecret, // OAuth2 client secret for authentication
		EnableTLSVerify: true,                       // Enforce TLS certificate validation
		RequestTimeout:  reqTimeout,                 // HTTP request timeout for API calls
		Scopes:          []string{},                 // OAuth2 scopes (empty for default access)
		MaxRetries:      maxRetries,                 // Retry attempts for transient failures
		RetryDelay:      retryDelay,                 // Base delay between retries
	}

	// Create MCMP client instance with OAuth2 authentication capabilities
	mcmpClient, err := mcmp.NewClient(mcmpConfig)
	if err != nil {
		return nil, fmt.Errorf("failed to create MCMP client: %w", err)
	}
	return mcmpClient, nil
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
	level := slog.LevelInfo
	if debug {
		level = slog.LevelDebug
	}

	logger = slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{
		Level: level,
	}))
}
