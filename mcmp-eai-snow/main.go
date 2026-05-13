package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"os"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-snow/pkg/clients/mcmp"
	"github.com/it-at-m/mcmp/mcmp-eai-snow/pkg/clients/snow"
	"github.com/it-at-m/mcmp/mcmp-eai-snow/pkg/processor"
	"github.com/spf13/viper"
)

// Global debug flag that controls verbose logging throughout the application
var (
	debug = false
)

// Application name constant used for configuration file naming and identification
const (
	appname = "mcmp-eai-snow"
)

// Configuration structs that define the structure of the TOML configuration file
// These structs are used by Viper to unmarshal the configuration into Go structs

// General contains general application settings
type General struct {
	Debug bool // Enables debug logging when set to true
}

// ServiceNow contains connection settings for ServiceNow instance
type ServiceNow struct {
	OAuthUrl          string // OAuth authentication server base URL
	OAuthClientId     string // OAuth 2.0 client identifier
	OAuthClientSecret string // OAuth 2.0 client secret for authentication
	ApiEndpoint       string // ServiceNow API endpoint URL where data will be posted
	ProxyUrl          string // Optional HTTP proxy URL for ServiceNow API calls
}

// Mcmp contains settings for the MCMP (Multi Cloud Management Platform) API
type Mcmp struct {
	OAuthUrl          string // Keycloak authentication server base URL
	OAuthRealm        string // Keycloak realm name where the client is configured
	OAuthClientId     string // OAuth 2.0 client identifier
	OAuthClientSecret string // OAuth 2.0 client secret for authentication
	ApiEndpoint       string // MCMP API endpoint URL where data will be posted
}

// Config is the root configuration structure that combines all configuration sections
// This structure mirrors the TOML configuration file format
type Config struct {
	GENERAL    General    // General application settings
	SERVICENOW ServiceNow // ServiceNow connection configuration
	MCMP       Mcmp       // MCMP API configuration
}

// main is the entry point of the application
// It handles command line argument parsing and delegates to the run function
// The application expects no command line arguments and will show usage if any are provided
func main() {
	// Define custom usage function that displays program usage information
	flag.Usage = func() {
		_, err := fmt.Fprintf(flag.CommandLine.Output(), "usage: %s\n", os.Args[0])
		if err != nil {
			log.Fatal(err)
		}
		flag.PrintDefaults()
	}

	// Parse command line flags (none are currently defined)
	flag.Parse()

	// Handle different numbers of command line arguments
	switch len(os.Args) {
	case 1:
		// No additional arguments - proceed with normal execution
		run()
	default:
		// Any additional arguments are considered an error
		_, err := fmt.Fprintln(os.Stderr, "error: wrong number of arguments")
		if err != nil {
			log.Fatal(err)
		}
		flag.Usage()
		os.Exit(1)
	}
}

// run executes the main application logic
// This function orchestrates the entire data synchronization process:
// 1. Loads configuration from TOML file
// 2. Creates ServiceNow client and processes application services
// 3. Retrieves and exports ServiceNow data
// 4. Authenticates with Keycloak to get access token
// 5. Sends processed data to MCMP API
func run() {
	// Load configuration from TOML file using the generic ReadConfig function
	cfg := ReadConfig[Config](appname)
	debug = cfg.GENERAL.Debug

	snowConfig := snow.ClientConfig{
		Debug:           debug,
		AuthServerURL:   cfg.SERVICENOW.OAuthUrl,
		ClientID:        cfg.SERVICENOW.OAuthClientId,
		ClientSecret:    cfg.SERVICENOW.OAuthClientSecret,
		ApiEndpoint:     cfg.SERVICENOW.ApiEndpoint,
		ProxyURL:        cfg.SERVICENOW.ProxyUrl,
		EnableTLSVerify: true,
		RequestTimeout:  30 * time.Second,
		Scopes:          []string{},
	}

	snowClient, err := snow.NewClient(snowConfig)
	if err != nil {
		log.Fatalf("Failed to create ServiceNow client: %v", err)
	}
	if debug {
		snowClient.EnableDebug()
	}

	// Create and configure ServiceProcessor for handling ServiceNow data operations
	// The processor encapsulates the business logic for data retrieval and transformation
	processor := processor.NewServiceProcessor(snowClient, debug)

	// Process application services from ServiceNow
	// This involves fetching app services and their related data (CIs, groups, users)
	err = processor.ProcessAppServices()
	if err != nil {
		log.Fatalf("Error processing AppServices: %v", err)
	}

	// Retrieve the processed ServiceNow data structure
	// This contains all collected data ready for export
	snowData := processor.GetSnowData()
	debugPrintf("SnowData created:")
	debugPrintf("- Users: %d", len(snowData.Users))
	debugPrintf("- Groups: %d", len(snowData.Groups))
	debugPrintf("- CIs: %d", len(snowData.CmdbCIs))
	debugPrintf("- AppServices: %d", len(snowData.AppServices))

	// Export ServiceNow data as JSON string for API transmission
	// The JSON format is required by the MCMP API for data ingestion
	jsonData, err := processor.ExportSnowDataAsJSON()
	if err != nil {
		log.Fatalf("Error during JSON export: %v", err)
	}
	debugPrintf("JSON export successful, length: %d characters", len(jsonData))

	// Export ServiceNow data to a local file for backup/debugging purposes
	// This creates a persistent copy of the data that was sent to MCMP
	err = processor.ExportSnowDataToFile("snowdata_export.json")
	if err != nil {
		log.Fatalf("Error during file export: %v", err)
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
		log.Fatalf("Failed to create MCMP client: %v", err)
	}
	if debug {
		mcmpClient.EnableDebug()
	}

	// Send processed SNow data to MCMP API endpoint
	// This completes the data synchronization process
	if err := mcmpClient.SendSNowData(context.Background(), cfg.MCMP.ApiEndpoint, []byte(jsonData)); err != nil {
		log.Fatalf("Error sending data to MCMP: %v", err)
	}
	debugPrintf("Data successfully sent to MCMP")
}

// ReadConfig is a generic function that loads configuration from a TOML file
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
//
// Configuration file search locations (in order):
// 1. $HOME/.{appname}/{appname}.toml
// 2. ./{appname}.toml (current directory)
// 3. /opt/lhm/scripts/{appname}.toml
//
// The function will terminate the application if the configuration file
// cannot be found, read, or parsed successfully.
func ReadConfig[T any](appname string) *T {
	// Set configuration file name (without extension)
	viper.SetConfigName(appname)

	// Set configuration file type to TOML format
	viper.SetConfigType("toml")

	// Add configuration file search paths
	// These paths are searched in the order they are added
	viper.AddConfigPath("$HOME/." + appname) // User-specific config in home directory
	viper.AddConfigPath(".")                 // Current working directory
	viper.AddConfigPath("/opt/lhm/scripts")  // System-wide configuration directory

	// Attempt to read the configuration file
	err := viper.ReadInConfig()
	if err != nil {
		log.Fatalf("Config error: %v", err)
	}

	// Create instance of the generic configuration type
	var cfg T

	// Unmarshal the configuration data into the struct
	// Viper automatically maps TOML keys to struct fields
	err = viper.Unmarshal(&cfg)
	if err != nil {
		log.Fatalf("Config unmarshal error: %v", err)
	}

	return &cfg
}

// debugPrintf provides conditional debug logging functionality
// It only outputs log messages when the global debug flag is enabled
// This allows for verbose logging during development and troubleshooting
// without cluttering production logs
//
// Parameters:
//   - format: Printf-style format string
//   - a: Variable arguments for format string placeholders
//
// The function uses the standard log package with timestamp prefixes
// Debug messages are sent to the default logger output (typically stderr)
func debugPrintf(format string, a ...interface{}) {
	if debug {
		log.Printf(format, a...)
	}
}
