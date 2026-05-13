package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"os"
	"path/filepath"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-checkmk/pkg/client/checkmk"
	"github.com/it-at-m/mcmp/mcmp-eai-checkmk/pkg/client/source"
	"github.com/it-at-m/mcmp/mcmp-eai-checkmk/pkg/processor"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/app"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/client/mcmp"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/config"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

// Application name constant used for configuration file naming and identification
const (
	appName = "mcmp-eai-checkmk"
)

// Global debug flag that controls verbose logging throughout the application
var (
	logger                    *logging.StructuredLogger
	ErrWrongNumberOfArguments = errors.New("wrong number of arguments")
	ErrNoCheckMKSource        = errors.New("at least one CheckMK source configuration is required")
)

// Configuration structs that define the structure of the TOML configuration file
// These structs are used by Viper to unmarshal the configuration into Go structs

// Config is the root configuration structure that combines all configuration sections
// This structure mirrors the TOML configuration file format
type Config struct {
	LOGGING logging.LogConfig
	CHECKMK []checkmk.Config
	MCMP    mcmp.Config
}

// main is the entry point of the application
// It handles command line argument parsing and delegates to the run function
// The application expects no command line arguments and will show usage if any are provided
func main() {
	app.Bootstrap(func(ctx context.Context) error {
		// 1. Flags parsen
		flag.Usage = func() {
			exePath, err := os.Executable()
			if err != nil {
				fmt.Fprintf(os.Stderr, "failed to get executable path: %v\n", err)
			}
			_, _ = fmt.Fprintf(flag.CommandLine.Output(), "Usage: %s\n", filepath.Base(exePath))
			flag.PrintDefaults()
		}
		flag.Parse()

		if len(os.Args) != 1 {
			return ErrWrongNumberOfArguments
		}

		// 2. Run Logic aufrufen
		return run(ctx)
	})
}

// run executes the main application logic
// This function orchestrates the entire data synchronization process:
// 1. Loads configuration from TOML file
// 2. Creates NetApp ONTAP clients for each configured source
// 3. Retrieves storage data from NetApp ONTAP systems
// 4. Authenticates with Keycloak to get access token
// 5. Sends processed data to MCMP API
//
// Parameters:
//   - ctx: Context for cancellation and timeout handling
//
// Returns:
//   - error: Any error that prevents successful execution
func run(ctx context.Context) error {
	cfg, err := config.LoadConfig[Config](appName)
	if err != nil {
		return fmt.Errorf("failed to load config: %w", err)
	}

	// Timeout for the entire operation
	timeout := 1 * time.Minute

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

	// Initialize MCMP Client
	mcmpConfig := cfg.MCMP.ToClientConfig()
	mcmpConfig.RequestTimeout = 30 * time.Second
	mcmpClient, err := mcmp.NewClient(ctx, mcmpConfig, logger)
	if err != nil {
		return fmt.Errorf("failed to create mcmp client: %w", err)
	}

	var sources []app.DataSource[*processor.CheckmkAggregatedData]
	for _, checkmkConfig := range cfg.CHECKMK {

		checkmkClient, err := checkmk.NewClient(checkmkConfig, logger)
		if err != nil {
			logger.Error("Failed to create CheckMK client", "hostname", checkmkConfig.Hostname, "error", err)
			continue
		}
		dataProcessor, err := processor.NewProcessor(checkmkClient, logger)
		if err != nil {
			logger.Error("Failed to create processor", "hostname", checkmkConfig.Hostname, "error", err)
			continue
		}

		sources = append(sources, source.NewCheckMkSource(
			checkmkConfig.Hostname,
			checkmkConfig.Enabled,
			dataProcessor,
			mcmpClient,
			cfg.MCMP.ApiEndpoint,
			logger,
		))
	}

	// Start generic EAI runner
	if err := app.RunEAI(ctx, app.EAIConfig{AppName: appName, LockEnabled: true}, sources, logger); err != nil {
		return fmt.Errorf("failed to run EAI: %w", err)
	}
	return nil
}

// validate validates the configuration struct to ensure all required fields are present
// This method performs comprehensive validation of all configuration sections
//
// Returns:
//   - error: Validation error if any required field is missing or invalid
func (c *Config) validate() error {
	if len(c.CHECKMK) == 0 {
		return ErrNoCheckMKSource
	}
	for i, oc := range c.CHECKMK {
		if err := oc.Validate(); err != nil {
			return fmt.Errorf("CheckMK[%d]: %w", i, err)
		}
	}
	if err := c.MCMP.Validate(); err != nil {
		return fmt.Errorf("MCMP-Validate: %w", err)
	}
	return nil
}
