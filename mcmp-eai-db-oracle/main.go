// Package main provides the entry point for the MCMP EAI Rightsizing service.
//
// This service analyzes server resource utilization metrics and generates rightsizing
// recommendations using a Kubernetes VPA-like algorithm. It retrieves CPU and memory
// consumption data from the MCMP API, processes it to calculate optimal resource
// allocations, and submits recommendations back to the system.
//
// ## Overview
//
// The MCMP EAI Rightsizing service is a component of the MCMP Enterprise Application
// Integration (EAI) suite. It automates the process of analyzing server workload
// patterns and recommending appropriate resource adjustments to optimize infrastructure
// utilization while maintaining service performance.
//
// ## Architecture
//
// The application follows a modular architecture with the following key components:
//
//   - Configuration Loading: Reads TOML-based configuration files for logging, MCMP
//     API connectivity, and processor settings
//   - MCMP Client: Communicates with the MCMP API to fetch server metrics and submit
//     recommendations
//   - Data Processor: Implements the rightsizing algorithm using statistical analysis
//     of historical metrics
//   - Data Source: Abstracts the data fetching and processing pipeline
//   - EAI Runner: Generic orchestrator that manages the entire pipeline execution
//
// ## Configuration
//
// The service requires a configuration file named `mcmp-eai-rightsizing.toml` in the
// current working directory or accessible via the MCMP_CONFIG_PATH environment variable.
// The configuration must include:
//
//   - LOGGING: Logging configuration (level, format, output)
//   - MCMP: MCMP API connection details and rightsizing endpoint
//   - PROCESSOR: Processor settings including worker count, percentile thresholds, and
//     minimum sample sizes
//
// ## Execution Flow
//
//  1. Application bootstrapping via app.Bootstrap()
//  2. Command-line flag parsing (no arguments accepted)
//  3. Configuration loading from TOML file
//  4. Logger initialization with structured logging
//  5. MCMP client creation for API communication
//  6. Data processor initialization with VPA-like algorithm
//  7. Data source setup for server metrics retrieval
//  8. Generic EAI runner orchestration
//  9. Submission of rightsizing recommendations
//
// 10. Graceful shutdown with context timeout
//
// ## Timeout Behavior
//
// The entire rightsizing analysis operation is bounded by a 10-minute timeout,
// suitable for analyzing approximately 6000 servers. This timeout provides:
//
//   - Sufficient time for MCMP API requests with 60-second request timeouts
//   - Parallel processing via configurable worker pools
//   - Failsafe mechanism to prevent indefinite execution
//
// ## Error Handling
//
// The service implements comprehensive error handling with wrapped error messages
// using the fmt.Errorf pattern with %w directive. This ensures:
//
//   - Traceability of error origin points
//   - Proper error context propagation
//   - Easy debugging and logging
//
// ## Logging
//
// Structured logging is used throughout the application, providing:
//
//   - Contextual information about execution progress
//   - Configuration details at startup
//   - Processing statistics upon completion
//   - Error details with full stack traces
//
// ## Usage
//
// Basic execution:
//
//	$ ./mcmp-eai-rightsizing
//
// The service will:
//   - Load configuration automatically
//   - Connect to MCMP API
//   - Analyze server metrics
//   - Generate and submit recommendations
//   - Log results to configured output
//
// ## Exit Codes
//
// The service exits with the standard Go application exit code:
//   - 0: Successful execution
//   - Non-zero: Execution failure (error details in logs)
//
// ## Dependencies
//
// Core dependencies include:
//   - mcmp-eai-common: Common utilities, app lifecycle, configuration, logging
//   - mcmp-eai-rightsizing: Domain-specific MCMP client, processor, and data source
//
// ## Performance Considerations
//
//   - Worker Pool: Configured number of concurrent workers for metrics processing
//   - Percentile Calculation: CPU and memory recommendations based on configured
//     percentiles (e.g., 90th percentile for resource allocations)
//   - Minimum Sample Size: Ensures statistical significance of recommendations
//     (minimum number of historical data points required)
//
// ## Concurrency Model
//
// The application uses:
//   - Context-based cancellation for graceful shutdown
//   - Configured worker pool for parallel processing
//   - Thread-safe logging throughout
//   - Timeout enforcement at the operation level
//
// ## Related Documentation
//
// For more information about specific components, refer to:
//   - processor: Algorithm implementation for rightsizing calculations
//   - mcmp/client: MCMP API client implementation
//   - source: Data source abstraction for metrics retrieval
package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"os"
	"path/filepath"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/app"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/client/mcmp"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/config"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/datasource"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
	"github.com/it-at-m/mcmp/mcmp-eai-db-oracle/pkg/client/source"
	"github.com/it-at-m/mcmp/mcmp-eai-db-oracle/pkg/processor"
)

// Application name constant used for configuration file naming and identification.
// This constant is used to construct the configuration file name:
// {appName}.toml (e.g., mcmp-eai-db-oracle.toml).
const (
	appName = "mcmp-eai-db-oracle"
)

// Global variables used throughout the application lifecycle.
//
// logger: The global structured logger instance initialized during run(). Used for
// logging operations across all functions and packages. Must be initialized before
// logging calls.
//
// ErrWrongNumberOfArguments: Error returned when the application receives unexpected
// command-line arguments. The service accepts no positional arguments; only flags
// (if any) should be provided.
var (
	logger                    *logging.StructuredLogger
	ErrWrongNumberOfArguments = errors.New("wrong number of arguments")
)

// Config is the root configuration structure that aggregates all configuration
// sections required by the application.
//
// This structure is directly mapped from the TOML configuration file, with each
// field corresponding to a top-level TOML section:
//
//   - LOGGING: Logging subsystem configuration
//   - MCMP: MCMP API client and endpoint configuration
//   - PROCESSOR: Rightsizing processor algorithm configuration
//
// The structure uses uppercase field names for automatic TOML unmarshaling via
// reflection in the config package.
//
// Example TOML structure:
//
//	[LOGGING]
//	level = "info"
//	format = "json"
//
//	[MCMP]
//	# MCMP API configuration
//
//	[PROCESSOR]
//	worker_count = 10
//	min_sample_size = 7
type Config struct {
	// MCMP holds MCMP API connection configuration and rightsizing endpoint details
	MCMP []mcmp.Config

	// LOGGING holds logging configuration including level, format, and output settings
	LOGGING logging.LogConfig

	// PROCESSOR holds rightsizing algorithm configuration including worker count,
	// percentile thresholds, and statistical parameters
	PROCESSOR processor.Config
}

// main is the primary entry point of the MCMP EAI Rightsizing application.
//
// Responsibilities:
//   - Manages application lifecycle via app.Bootstrap()
//   - Parses and validates command-line arguments
//   - Delegates execution to the run() function
//   - Ensures proper shutdown and error reporting
//
// The function wraps the actual logic in a bootstrap callback to leverage common
// lifecycle management provided by the mcmp-eai-common package. This ensures
// consistent initialization and cleanup across all EAI microservices.
//
// Command-line Behavior:
//   - Accepts no positional arguments; returns ErrWrongNumberOfArguments if provided
//   - Flag parsing is handled internally; standard -h or -help shows usage
//   - Usage output displays the executable name and available flags
//
// Error Handling:
//   - Wraps errors returned from run() with appropriate context
//   - Logs errors through the standard application error handling
//   - Returns non-zero exit code on failure
//
// Example:
//
//	$ ./mcmp-eai-rightsizing
//	# Executes successfully if configuration is valid
//
//	$ ./mcmp-eai-rightsizing extra_arg
//	# Fails with ErrWrongNumberOfArguments
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

// run executes the core application logic orchestrating the entire rightsizing
// analysis pipeline.
//
// This function is responsible for:
//  1. Loading and validating configuration from TOML file
//  2. Setting up context with operation timeout (10 minutes)
//  3. Initializing the structured logger
//  4. Validating configuration completeness
//  5. Creating MCMP API client for metrics retrieval
//  6. Initializing the processor with VPA-like algorithm
//  7. Setting up data sources for server metrics
//  8. Executing the generic EAI runner pipeline
//  9. Reporting completion status
//
// Configuration Load Phase:
//   - Loads configuration from {appName}.toml (mcmp-eai-rightsizing.toml)
//   - Returns wrapped error if configuration file is missing or invalid
//   - Configuration file should be in current directory or via MCMP_CONFIG_PATH
//
// Timeout Management:
//   - Establishes a 10-minute timeout for the entire operation
//   - Suitable for analyzing approximately 6000 servers
//   - Includes 60-second per-request timeout for MCMP API calls
//   - Timeout cancellation is deferred for proper cleanup
//
// Logger Initialization:
//   - Sets up global structured logger using configuration
//   - Logger is assigned to package-level variable for use throughout
//   - Logging level and format determined by configuration
//
// Configuration Validation:
//   - Validates MCMP configuration completeness
//   - Returns validation errors with context
//   - Ensures all required fields are present and valid
//
// Client Initialization:
//   - Creates MCMP client with longer request timeout (60 seconds)
//   - Uses common configuration converted from MCMP-specific config
//   - Returns errors if API connectivity cannot be established
//
// Processor Setup:
//   - Initializes processor with statistical algorithm configuration
//   - Processor uses configured percentiles for recommendations
//   - Worker pool size determines parallelism of analysis
//
// Data Source Creation:
//   - Creates rightsizing source with "mcmp" as hostname identifier
//   - Enables source immediately for data fetching
//   - Uses configured API endpoint for metrics retrieval
//   - Associates processor and client with source
//
// Logging During Execution:
//   - Logs configuration parameters at startup (workers, percentiles, sample size)
//   - Logs completion status upon successful execution
//   - Error logging handled by app.RunEAI()
//
// Parameters:
//   - ctx: Context from bootstrap providing cancellation and timeout control
//
// Returns:
//   - error: Nil on successful execution, wrapped error otherwise
//   - Errors include: config load failures, initialization errors, execution failures
//
// Example Return Cases:
//   - "failed to load config: stat mcmp-eai-rightsizing.toml: no such file or directory"
//   - "failed to initialize logger: invalid log level"
//   - "invalid configuration: MCMP configuration invalid: missing API endpoint"
//   - "failed to create MCMP client: connection refused"
//   - "failed to run EAI: context deadline exceeded"
//
// Post-Execution State:
//   - If successful, all recommendations have been submitted to MCMP
//   - If failed, error details are available in structured logs
//   - Resources are cleaned up regardless of success or failure
func run(ctx context.Context) error {
	cfg, err := config.LoadConfig[Config](appName)
	if err != nil {
		return fmt.Errorf("failed to load config: %w", err)
	}

	timeout := 40 * time.Minute
	ctx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	// Initialize Logger using the centralized setup from common
	logger, err = logging.SetupGlobalLogger(cfg.LOGGING)
	if err != nil {
		return fmt.Errorf("failed to initialize logger: %w", err)
	}

	// Configuration validation
	if validationErr := cfg.validate(); validationErr != nil {
		return fmt.Errorf("invalid configuration: %w", validationErr)
	}

	var mcmpClients []datasource.JSONSender
	var apiEndpoints []string
	var discoveryClient *mcmp.Client

	for _, mcmpCfg := range cfg.MCMP {
		client, err := mcmp.NewClient(ctx, mcmpCfg.ToClientConfig(), logger)
		if err != nil {
			return fmt.Errorf("failed to create MCMP client: %w", err)
		}
		mcmpClients = append(mcmpClients, client)
		apiEndpoints = append(apiEndpoints, mcmpCfg.ApiEndpoint)

		if client.IsDiscoveryBackend() {
			if discoveryClient != nil {
				return fmt.Errorf("multiple MCMP backends configured with DiscoveryBackend = true (only one allowed)")
			}
			discoveryClient = client
		}
	}

	if discoveryClient == nil {
		return fmt.Errorf("no MCMP backend configured with DiscoveryBackend = true")
	}

	// Initialize Processor with the single discovery client
	dataProcessor, err := processor.NewProcessor(discoveryClient, logger, cfg.PROCESSOR)
	if err != nil {
		return fmt.Errorf("failed to create processor: %w", err)
	}

	// Create a data source for oracle database metrics (sending to all configured MCMP clients)
	sources := []app.DataSource[*processor.OracleExport]{
		source.NewOracleSource(
			"mcmp",
			true,
			dataProcessor,
			mcmpClients,
			apiEndpoints,
			logger,
		),
	}

	logger.Info("Starting oracle database analysis",
		"workers", cfg.PROCESSOR.WorkerCount)

	// Start generic EAI runner
	if err := app.RunEAI(ctx, app.EAIConfig{AppName: appName, LockEnabled: false}, sources, logger); err != nil {
		return fmt.Errorf("failed to run EAI: %w", err)
	}

	logger.Info("Rightsizing analysis completed successfully")
	return nil
}

// validate validates the application configuration structure to ensure all required
// fields are present and valid.
//
// This method performs comprehensive validation of all configuration sections by
// delegating to component-specific validators:
//   - MCMP configuration: Validates API endpoint, authentication, and base URL
//   - Additional sections: Can be extended for LOGGING and PROCESSOR validation
//
// Validation Scope:
//   - Checks that all required configuration fields are present
//   - Verifies configuration values are in valid ranges
//   - Ensures interdependencies between sections are satisfied
//   - Returns specific error messages indicating which section failed
//
// Current Validation:
//   - MCMP.Validate(): Performs MCMP-specific validation checks
//   - Future: Can be extended with logging and processor validation
//
// Parameters:
//   - c: Pointer to Config struct to validate (receiver)
//
// Returns:
//   - error: Nil if all validations pass, wrapped error with context otherwise
//   - Error format: "MCMP configuration invalid: {detailed_validation_error}"
//
// Example Return Cases:
// ... existing code ...
//   - "MCMP configuration invalid: missing authentication credentials"
//
// Integration:
// ... existing code ...
//   - Provide clear error messages for configuration correction
//
// Best Practices:
// ... existing code ...
//   - Keep error messages descriptive for troubleshooting
//   - Validate constraints specific to each component
func (c *Config) validate() error {
	if len(c.MCMP) == 0 {
		return fmt.Errorf("MCMP configuration invalid: at least one MCMP backend is required")
	}
	discoveryCount := 0
	for _, m := range c.MCMP {
		if err := m.Validate(); err != nil {
			return fmt.Errorf("MCMP configuration invalid: %w", err)
		}
		if m.DiscoveryBackend {
			discoveryCount++
			if m.OracleServerEndpoint == "" {
				return fmt.Errorf("MCMP configuration invalid: OracleServerEndpoint is required for DiscoveryBackend")
			}
		}
	}
	if discoveryCount != 1 {
		return fmt.Errorf("MCMP configuration invalid: exactly one DiscoveryBackend must be true, got %d", discoveryCount)
	}
	if c.PROCESSOR.OracleUser == "" {
		return fmt.Errorf("PROCESSOR configuration invalid: OracleUser is required")
	}
	if c.PROCESSOR.OraclePassword == "" {
		return fmt.Errorf("PROCESSOR configuration invalid: OraclePassword is required")
	}
	return nil
}
