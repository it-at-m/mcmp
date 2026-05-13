package main

import (
	"context"
	"flag"
	"fmt"
	"os"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/app"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/client/mcmp"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/config"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
	"github.com/it-at-m/mcmp/mcmp-eai-netapp-storagegrid/pkg/client/netapp/storagegrid"
	"github.com/it-at-m/mcmp/mcmp-eai-netapp-storagegrid/pkg/client/source"
	"github.com/it-at-m/mcmp/mcmp-eai-netapp-storagegrid/pkg/processor"
)

// Application name constant used for configuration file naming and identification
const (
	appName = "mcmp-eai-netapp-storagegrid"
)

// Global debug flag that controls verbose logging throughout the application
var (
	logger *logging.StructuredLogger
)

// Configuration structs that define the structure of the TOML configuration file
// These structs are used by Viper to unmarshal the configuration into Go structs

// Config is the root configuration structure that combines all configuration sections
// This structure mirrors the TOML configuration file format
type Config struct {
	LOGGING     logging.LogConfig
	STORAGEGRID []storagegrid.Config // Renamed from ONTAP to STORAGEGRID
	MCMP        mcmp.Config          // MCMP API configuration
	GENERAL     struct {
		Timeout int // Timeout in seconds, default 300
	}
}

// main is the entry point of the application
// It handles command line argument parsing and delegates to the run function
// The application expects no command line arguments and will show usage if any are provided
func main() {
	app.Bootstrap(func(ctx context.Context) error {
		// 1. Flags parsen
		flag.Usage = func() {
			_, _ = fmt.Fprintf(flag.CommandLine.Output(), "Usage: %s\n", os.Args[0])
			flag.PrintDefaults()
		}
		flag.Parse()

		if len(os.Args) != 1 {
			return fmt.Errorf("wrong number of arguments")
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
	timeout := 5 * time.Minute
	if cfg.GENERAL.Timeout > 0 {
		timeout = time.Duration(cfg.GENERAL.Timeout) * time.Second
	}
	ctx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	// Initialize Logger using the centralized setup from common
	logger, err = logging.SetupGlobalLogger(cfg.LOGGING)
	if err != nil {
		return fmt.Errorf("failed to initialize logger: %w", err)
	}

	// Configuration validation
	if err := cfg.validate(); err != nil {
		return fmt.Errorf("invalid configuration: %w", err)
	}

	// Initialize MCMP Client
	mcmpConfig := cfg.MCMP.ToClientConfig()
	mcmpConfig.RequestTimeout = 30 * time.Second
	mcmpClient, err := mcmp.NewClient(ctx, mcmpConfig, logger)
	if err != nil {
		return fmt.Errorf("failed to create mcmp client: %w", err)
	}

	// Prepare data sources
	var sources []app.DataSource[*storagegrid.StorageGridData]
	for _, sgConfig := range cfg.STORAGEGRID {
		sgClient, err := storagegrid.NewClient(sgConfig, logger)
		if err != nil {
			logger.Error("Failed to create StorageGRID client", "hostname", sgConfig.Hostname, "error", err)
			continue
		}

		dataProcessor, err := processor.NewProcessor(sgClient, processor.DefaultConcurrency, logger)
		if err != nil {
			logger.Error("Failed to create processor", "hostname", sgConfig.Hostname, "error", err)
			continue
		}

		sources = append(sources, source.NewStorageGridSource(
			sgConfig.Hostname,
			sgConfig.Enabled,
			dataProcessor,
			mcmpClient,
			cfg.MCMP.ApiEndpoint,
			logger,
		))
	}

	// Start generic EAI runner
	return app.RunEAI(ctx, app.EAIConfig{
		AppName:     appName,
		LockEnabled: true,
	}, sources, logger)
}

// validate validates the configuration struct to ensure all required fields are present
// This method performs comprehensive validation of all configuration sections
//
// Returns:
//   - error: Validation error if any required field is missing or invalid
func (c *Config) validate() error {
	if len(c.STORAGEGRID) == 0 {
		return fmt.Errorf("at least one StorageGRID source configuration is required")
	}
	for i, sc := range c.STORAGEGRID {
		if err := sc.Validate(); err != nil {
			return fmt.Errorf("STORAGEGRID[%d]: %w", i, err)
		}
	}
	if err := c.MCMP.Validate(); err != nil {
		return err
	}
	return nil
}
