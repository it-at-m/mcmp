package main

import (
	"context"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"log/slog"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	awx "github.com/euerla/goawx/client"
	"github.com/it-at-m/mcmp/mcmp-eai-awx/pkg/clients/mcmp"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/config"
)

// Global debug flag that controls verbose logging throughout the application
var (
	debug  = false
	logger *slog.Logger
)

// Application name constant used for configuration file naming and identification
const (
	appName            = "mcmp-eai-awx"
	defaultTimeout     = 20 * time.Minute
	httpRequestTimeout = 60 * time.Second
	signalBufferSize   = 1
	awxPageSize        = "500"
)

type InventoryResult struct {
	LinuxHosts                  []InventoryHost `json:"linux_hosts"`
	WindowsHosts                []InventoryHost `json:"windows_hosts"`
	WindowsMaintenanceModeHosts []InventoryHost `json:"windows_maintenance_mode_hosts"`
}

type InventoryHost struct {
	Created    time.Time `json:"created"`
	FQDN       string    `json:"fqdn"`
	User       string    `json:"user"`
	ValidUntil string    `json:"valid_until"`
}

func (sp *InventoryResult) ExportAsJSON() (string, error) {
	jsonData, err := json.MarshalIndent(sp, "", "  ")
	if err != nil {
		return "", fmt.Errorf("error during JSON marshal: %w", err)
	}
	return string(jsonData), nil
}

func (sp *InventoryResult) ExportToFile(filename string) error {
	// Generate JSON representation of the data
	jsonString, err := sp.ExportAsJSON()
	if err != nil {
		return fmt.Errorf("error creating JSON: %w", err)
	}

	// Write JSON data to file with appropriate permissions (owner read/write, group/others read)
	err = os.WriteFile(filename, []byte(jsonString), 0o644)
	if err != nil {
		return fmt.Errorf("error writing file %s: %w", filename, err)
	}

	logDebugf("AWX Inventory data successfully exported to file %s", filename)
	return nil
}

// Configuration structs that define the structure of the TOML configuration file
// These structs are used by Viper to unmarshal the configuration into Go structs

// GeneralConfig contains general application settings
type GeneralConfig struct {
	Debug bool // Enables debug logging when set to true
}

// AwxConfig  contains connection settings for AWX instance
type AwxConfig struct {
	Enabled                           bool   `mapstructure:"Enabled"`
	Username                          string `mapstructure:"Username"`
	Password                          string `mapstructure:"Password"`
	ApiEndpoint                       string `mapstructure:"ApiEndpoint"`
	LinuxRootPermitsInventoryId       int    `mapstructure:"LinuxRootPermitsInventoryId"`
	WindowsAdminPermitsInventoryId    int    `mapstructure:"WindowsAdminPermitsInventoryId"`
	WindowsMaintenanceModeInventoryId int    `mapstructure:"WindowsMaintenanceModeInventoryId"`
}

// McmpConfig contains settings for the MCMP (Multi Cloud Management Platform) API
type McmpConfig struct {
	OAuthUrl          string `mapstructure:"OAuthUrl"`          // Keycloak authentication server base URL
	OAuthRealm        string `mapstructure:"OAuthRealm"`        // Keycloak realm name where the client is configured
	OAuthClientId     string `mapstructure:"OAuthClientId"`     // OAuth 2.0 client identifier
	OAuthClientSecret string `mapstructure:"OAuthClientSecret"` // OAuth 2.0 client secret for authentication
	ApiEndpoint       string `mapstructure:"ApiEndpoint"`       // MCMP API endpoint URL where data will be posted
}

// Config is the root configuration structure that combines all configuration sections
// This structure mirrors the TOML configuration file format
type Config struct {
	GENERAL GeneralConfig // General application settings
	AWX     []AwxConfig   // ServiceNow connection configuration
	MCMP    McmpConfig    // MCMP API configuration
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
	// Initialize cancellable context for coordinated application lifecycle.go management
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
	// across all application components including API client and data processing
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
		_, err := fmt.Fprintf(flag.CommandLine.Output(), "Usage: %s\n", os.Args[0])
		if err != nil {
			return
		}

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

	// Create InventoryResult structure
	inventoryResult := InventoryResult{
		LinuxHosts:                  make([]InventoryHost, 0),
		WindowsHosts:                make([]InventoryHost, 0),
		WindowsMaintenanceModeHosts: make([]InventoryHost, 0),
	}

	for _, awxConfig := range cfg.AWX {
		if awxConfig.Enabled {
			awxClient, err := awx.NewAWX(awxConfig.ApiEndpoint, awxConfig.Username, awxConfig.Password, nil)
			if err != nil {
				return fmt.Errorf("failed to create %s AWX client : %w", awxConfig.ApiEndpoint, err)
			}
			params := map[string]string{
				"page_size": awxPageSize,
			}
			linuxRootPermits, err := awxClient.InventoriesService.GetHostsByInventoryID(awxConfig.LinuxRootPermitsInventoryId, params)
			if err != nil {
				return fmt.Errorf("failed to get Linux root permits inventory from %s : %w", awxConfig.ApiEndpoint, err)
			}
			processHosts(linuxRootPermits.Results, &inventoryResult.LinuxHosts, "Linux Root Permits", logger)

			windowsRootPermits, err := awxClient.InventoriesService.GetHostsByInventoryID(awxConfig.WindowsAdminPermitsInventoryId, params)
			if err != nil {
				return fmt.Errorf("failed to get Windows admin permits inventory from %s : %w", awxConfig.ApiEndpoint, err)
			}
			processHosts(windowsRootPermits.Results, &inventoryResult.WindowsHosts, "Windows Admin Permits", logger)

			windowsMaintenanceModeHosts, err := awxClient.InventoriesService.GetHostsByInventoryID(awxConfig.WindowsMaintenanceModeInventoryId, params)
			if err != nil {
				return fmt.Errorf("failed to get Windows maintenance mode inventory from %s : %w", awxConfig.ApiEndpoint, err)
			}
			processHosts(windowsMaintenanceModeHosts.Results, &inventoryResult.WindowsMaintenanceModeHosts, "Windows Maintenance", logger)
		}
	}
	// Debug output if needed
	logDebugf("Processed inventory result: Linux hosts: %d, Windows hosts: %d, Windows Maintenance hosts: %d",
		len(inventoryResult.LinuxHosts), len(inventoryResult.WindowsHosts), len(inventoryResult.WindowsMaintenanceModeHosts))

	// Create persistent backup file for audit trail and debugging purposes
	// The local file serves as a record of data transmitted to MCMP for troubleshooting
	err = inventoryResult.ExportToFile("awx_inventory.json")
	if err != nil {
		return fmt.Errorf("failed to export inventory result to file: %w", err)
	}

	mcmpClient, err := createMCMPClient(cfg)
	if err != nil {
		return fmt.Errorf("failed to create MCMP client: %w", err)
	}
	if cfg.GENERAL.Debug {
		mcmpClient.EnableDebug()
	}

	jsonData, err := inventoryResult.ExportAsJSON()
	if err != nil {
		return fmt.Errorf("failed to export inventory result to JSON: %w", err)
	}
	logDebugf("Exported inventory result to JSON: %s", jsonData)
	// Transmit processed host data to MCMP API endpoint using secure HTTP POST
	// This completes the data synchronization pipeline with comprehensive error handling
	if err := mcmpClient.SendAWXInventory(ctx, cfg.MCMP.ApiEndpoint, []byte(jsonData)); err != nil {
		return fmt.Errorf("error sending data to MCMP: %w", err)
	}
	logDebugf("Data successfully sent to MCMP")
	return nil
}

// loadAndValidateConfig loads and validates the application configuration from TOML files.
// Returns a configuration struct and error if configuration loading or validation fails.
func loadAndValidateConfig() (*Config, error) {
	// Load and validate application configuration from TOML files
	// Uses generic configuration reader for type-safe configuration loading with validation
	cfg, err := config.LoadConfig[Config](appName)
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

// createMCMPClient initializes and configures an MCMP client with OAuth2 authentication and secure communication.
// It utilizes the provided configuration to establish settings such as OAuth2 parameters, TLS verification, and timeouts.
// Returns an instance of the MCMP client or an error if configuration or client creation fails.
func createMCMPClient(cfg *Config) (*mcmp.Client, error) {
	// Configure MCMP client with OAuth2 authentication and secure communication settings
	// The configuration ensures proper authentication and secure data transmission
	mcmpConfig := mcmp.ClientConfig{
		Debug:           debug,                      // Enable debug logging for OAuth2 and API communication
		AuthServerURL:   cfg.MCMP.OAuthUrl,          // OAuth2 authorization server endpoint
		Realm:           cfg.MCMP.OAuthRealm,        // Authentication realm identifier
		ClientID:        cfg.MCMP.OAuthClientId,     // OAuth2 client identifier
		ClientSecret:    cfg.MCMP.OAuthClientSecret, // OAuth2 client secret for authentication
		EnableTLSVerify: true,                       // Enforce TLS certificate validation
		RequestTimeout:  httpRequestTimeout,         // HTTP request timeout for API calls
		Scopes:          []string{},                 // OAuth2 scopes (empty for default access)
	}

	// Create MCMP client instance with OAuth2 authentication capabilities
	// The client handles automatic token acquisition, refresh, and injection
	return mcmp.NewClient(mcmpConfig)
}

// validate validates the configuration fields and returns an error with all validation failures if any are found.
func (c *Config) validate() error {
	var errorList []string
	for i, awxConfig := range c.AWX {
		if awxConfig.Enabled {
			if awxConfig.Username == "" {
				errorList = append(errorList, fmt.Sprintf("AWX[%d] username is required", i))
			}
			if awxConfig.Password == "" {
				errorList = append(errorList, fmt.Sprintf("AWX[%d] password is required", i))
			}
			if awxConfig.ApiEndpoint == "" {
				errorList = append(errorList, fmt.Sprintf("AWX[%d] API endpoint is required", i))
			}
			if awxConfig.LinuxRootPermitsInventoryId <= 0 {
				errorList = append(errorList, fmt.Sprintf("AWX[%d] Linux inventory ID must be positive", i))
			}
			if awxConfig.WindowsAdminPermitsInventoryId <= 0 {
				errorList = append(errorList, fmt.Sprintf("AWX[%d] Windows inventory ID must be positive", i))
			}
			if awxConfig.WindowsMaintenanceModeInventoryId <= 0 {
				errorList = append(errorList, fmt.Sprintf("AWX[%d] Windows Maintenance Mode inventory ID must be positive", i))
			}
		}
	}
	if c.MCMP.OAuthUrl == "" {
		errorList = append(errorList, "MCMP OAuth URL is required")
	}
	if c.MCMP.OAuthRealm == "" {
		errorList = append(errorList, "MCMP OAuth realm is required")
	}
	if c.MCMP.OAuthClientId == "" {
		errorList = append(errorList, "MCMP OAuth client ID is required")
	}
	if c.MCMP.OAuthClientSecret == "" {
		errorList = append(errorList, "MCMP OAuth client secret is required")
	}
	if c.MCMP.ApiEndpoint == "" {
		errorList = append(errorList, "MCMP API endpoint is required")
	}

	if len(errorList) > 0 {
		return fmt.Errorf("validation failed: %s", strings.Join(errorList, "; "))
	}

	return nil
}

func (c *Config) String() string {
	return fmt.Sprintf("Config{Debug: %v, MCMP: %s }",
		c.GENERAL.Debug,
		//		redactSensitive(c.FOREMAN.Username),
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

// createInventoryHostFromVariables creates an InventoryHost from a variables map
// and the creation timestamp from the AWX result
func createInventoryHostFromVariables(variables map[string]interface{}, created time.Time) *InventoryHost {
	// Extract required keys from variables map
	fqdn, fqdnOk := variables["fqdn"].(string)
	user, userOk := variables["user"].(string)
	if !userOk {
		user, userOk = variables["requester_username"].(string)
	}
	if fqdnOk {
		fqdn = strings.ToLower(strings.TrimSpace(fqdn))
	}
	if userOk {
		user = strings.ToLower(strings.TrimSpace(user))
	}
	validUntil, validUntilOk := variables["valid_until"].(string)

	// Check if all required fields are present
	if !fqdnOk || !userOk || !validUntilOk {
		logErrorf("Missing required variables in inventory host: fqdn=%v, user=%v, valid_until=%v",
			fqdnOk, userOk, validUntilOk)
		return nil
	}

	// Verwende den bereits geparsten time.Time direkt
	return &InventoryHost{
		Created:    created,
		FQDN:       fqdn,
		User:       user,
		ValidUntil: validUntil,
	}
}

// processHosts processes AWX hosts and adds them to the appropriate host slice
func processHosts(results []*awx.Host, hostSlice *[]InventoryHost, hostType string, logger *slog.Logger) {
	if len(results) == 0 {
		logger.Debug("No hosts found", "type", hostType)
		return
	}

	logDebugf("Processing %d %s hosts", len(results), hostType)

	for _, host := range results {
		if host == nil {
			logErrorf("Encountered nil host in %s permits", hostType)
			continue
		}

		variables := host.GetVariablesAsMap()
		if variables == nil {
			logErrorf("Host %v has no variables", host)
			continue
		}

		inventoryHost := createInventoryHostFromVariables(variables, host.Created)
		if inventoryHost != nil {
			*hostSlice = append(*hostSlice, *inventoryHost)
			logDebugf("Added %s host, fqdn: %s, user: %s, valid_until: %s", hostType, inventoryHost.FQDN, inventoryHost.User, inventoryHost.ValidUntil)
		}
	}

	logDebugf("Finished processing %s hosts. Total added: %d", hostType, len(*hostSlice))
}
