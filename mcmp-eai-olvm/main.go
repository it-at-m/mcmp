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
	"github.com/it-at-m/mcmp/mcmp-eai-olvm/pkg/client/olvm"
	"github.com/it-at-m/mcmp/mcmp-eai-olvm/pkg/client/source"
	"github.com/it-at-m/mcmp/mcmp-eai-olvm/pkg/processor"
)

// Application name constant used for configuration file naming and identification
const (
	appName = "mcmp-eai-olvm"
)

// Global debug flag that controls verbose logging throughout the application
var (
	logger                    *logging.StructuredLogger
	ErrWrongNumberOfArguments = errors.New("wrong number of arguments")
	ErrNoOLVMSource           = errors.New("at least one OLVM source configuration is required")
	ErrNoMCMPConfig           = errors.New("at least one MCMP configuration is required")
)

// Configuration structs that define the structure of the TOML configuration file
// These structs are used by Viper to unmarshal the configuration into Go structs

// Config is the root configuration structure that combines all configuration sections
// This structure mirrors the TOML configuration file format
type Config struct {
	LOGGING logging.LogConfig
	OLVM    []olvm.Config
	MCMP    []mcmp.Config
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
	cfg, err := config.LoadConfig[Config](appName)
	if err != nil {
		return fmt.Errorf("failed to load config: %w", err)
	}

	// Set timeout for the entire operation
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
	var mcmpClients []datasource.JSONSender
	var apiEndpoints []string
	for _, mcmpCfg := range cfg.MCMP {
		clientCfg := mcmpCfg.ToClientConfig()
		clientCfg.RequestTimeout = 30 * time.Second

		mcmpClient, err := mcmp.NewClient(ctx, clientCfg, logger)
		if err != nil {
			return fmt.Errorf("failed to create MCMP client: %w", err)
		}
		mcmpClients = append(mcmpClients, mcmpClient)
		apiEndpoints = append(apiEndpoints, mcmpCfg.ApiEndpoint)
	}

	var sources []app.DataSource[*processor.Cloud]
	for _, olvmConfig := range cfg.OLVM {
		olvmClient, err := olvm.NewClient(ctx, olvmConfig.ToClientConfig(), logger)
		if err != nil {
			logger.Error("Failed to create OLVM client", "hostname", olvmConfig.Hostname, "error", err)
			continue
		}

		dataProcessor, err := processor.NewProcessor(olvmClient, logger)
		if err != nil {
			logger.Error("Failed to create processor", "hostname", olvmConfig.Hostname, "error", err)
			continue
		}

		sources = append(sources, source.NewOLVMSource(
			olvmConfig.Hostname,
			olvmConfig.Enabled,
			dataProcessor,
			mcmpClients,
			apiEndpoints,
			logger,
		))
	}
	// Start generic EAI runner
	if err := app.RunEAI(ctx, app.EAIConfig{AppName: appName, LockEnabled: true}, sources, logger); err != nil {
		return fmt.Errorf("failed to run EAI: %w", err)
	}

	return nil
}

func (c *Config) validate() error {
	if len(c.OLVM) == 0 {
		return ErrNoOLVMSource
	}
	for i, oc := range c.OLVM {
		if err := oc.Validate(); err != nil {
			return fmt.Errorf("OLVM[%d]: %w", i, err)
		}
	}
	if len(c.MCMP) == 0 {
		return ErrNoMCMPConfig
	}
	for i, mcmpCfg := range c.MCMP {
		if err := mcmpCfg.Validate(); err != nil {
			return fmt.Errorf("MCMP[%d]: %w", i, err)
		}
	}
	return nil
}
