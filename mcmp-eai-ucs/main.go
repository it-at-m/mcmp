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
	"github.com/it-at-m/mcmp/mcmp-eai-ucs/pkg/client/source"
	"github.com/it-at-m/mcmp/mcmp-eai-ucs/pkg/client/ucs"
	"github.com/it-at-m/mcmp/mcmp-eai-ucs/pkg/processor"
)

// Application name constant used for configuration file naming and identification
const (
	appName = "mcmp-eai-ucs"
)

// Global debug flag that controls verbose logging throughout the application
var (
	logger                    *logging.StructuredLogger
	ErrWrongNumberOfArguments = errors.New("wrong number of arguments")
	ErrNoCheckMKSource        = errors.New("at least one UCSM source configuration is required")
	ErrNoMCMPConfig           = errors.New("at least one MCMP configuration is required")
)

// Configuration structs that define the structure of the TOML configuration file
// These structs are used by Viper to unmarshal the configuration into Go structs

// Config is the root configuration structure that combines all configuration sections
// This structure mirrors the TOML configuration file format
type Config struct {
	LOGGING logging.LogConfig
	UCSM    []ucs.Config
	CIMC    []ucs.Config
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

	// Set timeout for the entire operation (10 minutes for ~6000 servers)
	timeout := 10 * time.Minute
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
	addUCSSource(cfg.UCSM, false, &sources, mcmpClients, apiEndpoints, logger)
	addUCSSource(cfg.CIMC, true, &sources, mcmpClients, apiEndpoints, logger)

	// Start generic EAI runner
	if err := app.RunEAI(ctx, app.EAIConfig{AppName: appName, LockEnabled: true}, sources, logger); err != nil {
		return fmt.Errorf("failed to run EAI: %w", err)
	}

	return nil
}

func (c *Config) validate() error {
	if len(c.UCSM) == 0 {
		return ErrNoCheckMKSource
	}
	for i, oc := range c.UCSM {
		if err := oc.Validate(); err != nil {
			return fmt.Errorf("CheckMK[%d]: %w", i, err)
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

func addUCSSource(configs []ucs.Config, isCIMC bool, sources *[]app.DataSource[*processor.Cloud], mcmpClients []datasource.JSONSender, apiEndpoints []string, logger *logging.StructuredLogger) {
	for _, c := range configs {
		clientType := "UCSM"
		if isCIMC {
			clientType = "CIMC"
		}

		ucsClient, err := ucs.NewClient(c, logger, isCIMC)
		if err != nil {
			logger.Error("Failed to create "+clientType+" client", "hostname", c.Hostname, "error", err)
			continue
		}

		dataProcessor, err := processor.NewProcessor(ucsClient, logger)
		if err != nil {
			logger.Error("Failed to create processor", "hostname", c.Hostname, "error", err)
			continue
		}

		*sources = append(*sources, source.NewUCSSource(
			c.Hostname,
			c.Enabled,
			dataProcessor,
			mcmpClients,
			apiEndpoints,
			logger,
			isCIMC,
		))
	}
}
