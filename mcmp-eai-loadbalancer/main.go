package main

import (
	"context"
	"encoding/json"
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
	"github.com/it-at-m/mcmp/mcmp-eai-loadbalancer/pkg/client/loadbalancer"
	"github.com/it-at-m/mcmp/mcmp-eai-loadbalancer/pkg/client/source"
	"github.com/it-at-m/mcmp/mcmp-eai-loadbalancer/pkg/processor"
)

const appName = "mcmp-eai-loadbalancer"

var logger *logging.StructuredLogger

// Config is the root configuration structure for mcmp-eai-loadbalancer.
type Config struct {
	LOGGING      logging.LogConfig
	LOADBALANCER loadbalancer.ClientConfig
	MCMP         []mcmp.Config
}

func main() {
	app.Bootstrap(func(ctx context.Context) error {
		skipUpload := flag.Bool("skip-upload", false, "write export JSON but do not send it to the MCMP backend")
		skipFetch := flag.Bool("skip-fetch", false, "skip the loadbalancer API call and use the existing "+source.ExportFilename+" instead")

		flag.Usage = func() {
			exePath, err := os.Executable()
			if err != nil {
				fmt.Fprintf(os.Stderr, "failed to get executable path: %v\n", err)
			}
			_, _ = fmt.Fprintf(flag.CommandLine.Output(), "Usage: %s [options]\n\nOptions:\n", filepath.Base(exePath))
			flag.PrintDefaults()
		}
		flag.Parse()

		return run(ctx, *skipFetch, *skipUpload)
	})
}

func run(ctx context.Context, skipFetch, skipUpload bool) error {
	cfg, err := config.LoadConfig[Config](appName)
	if err != nil {
		return fmt.Errorf("failed to load config: %w", err)
	}

	ctx, cancel := context.WithTimeout(ctx, 5*time.Minute)
	defer cancel()

	logger, err = logging.SetupGlobalLogger(cfg.LOGGING)
	if err != nil {
		return fmt.Errorf("failed to initialize logger: %w", err)
	}

	if err := cfg.validate(skipFetch, skipUpload); err != nil {
		return fmt.Errorf("invalid configuration: %w", err)
	}

	// Build the data fetcher — either live API or local file.
	var fetcher datasource.DataFetcher[*processor.LoadBalancerData]
	if skipFetch {
		logger.Info("--skip-fetch active: reading data from local file", "file", source.ExportFilename)
		fetcher = readFromLocalFile
	} else {
		lbClient, err := loadbalancer.NewClient(cfg.LOADBALANCER, logger)
		if err != nil {
			return fmt.Errorf("failed to create loadbalancer client: %w", err)
		}
		proc, err := processor.NewProcessor(lbClient, logger)
		if err != nil {
			return fmt.Errorf("failed to create processor: %w", err)
		}
		fetcher = proc.FetchData
	}

	// Build the MCMP sender — nil when upload is skipped.
	src := &datasource.JsonFileSource[*processor.LoadBalancerData]{
		Hostname:       "loadbalancer",
		Enabled:        cfg.LOADBALANCER.Enabled,
		ExportFilename: source.ExportFilename,
		Logger:         logger,
		Fetcher:        fetcher,
	}

	if skipUpload {
		logger.Info("--skip-upload active: data will be written to file but not sent to MCMP")
	} else {
		for i, mcmpCfg := range cfg.MCMP {
			clientConfig := mcmpCfg.ToClientConfig()
			clientConfig.RequestTimeout = 60 * time.Second
			mcmpClient, err := mcmp.NewClient(ctx, clientConfig, logger)
			if err != nil {
				return fmt.Errorf("failed to create MCMP client[%d]: %w", i, err)
			}
			src.McmpClients = append(src.McmpClients, mcmpClient)
			src.ApiEndpoints = append(src.ApiEndpoints, mcmpCfg.ApiEndpoint)
		}
	}

	if err := app.RunEAI(ctx, app.EAIConfig{AppName: appName, LockEnabled: true}, []app.DataSource[*processor.LoadBalancerData]{src}, logger); err != nil {
		return fmt.Errorf("failed to run EAI: %w", err)
	}
	return nil
}

// readFromLocalFile is used when --skip-fetch is set. It deserialises the previously
// written export file into LoadBalancerData so the rest of the pipeline can proceed
// (e.g. to re-upload without hitting the loadbalancer API again).
func readFromLocalFile(_ context.Context) (*processor.LoadBalancerData, error) {
	raw, err := os.ReadFile(source.ExportFilename)
	if err != nil {
		return nil, fmt.Errorf("failed to read %s: %w", source.ExportFilename, err)
	}
	var data processor.LoadBalancerData
	if err := json.Unmarshal(raw, &data); err != nil {
		return nil, fmt.Errorf("failed to parse %s: %w", source.ExportFilename, err)
	}
	return &data, nil
}

func (c *Config) validate(skipFetch, skipUpload bool) error {
	if !skipFetch {
		if err := c.LOADBALANCER.Validate(); err != nil {
			return fmt.Errorf("LOADBALANCER: %w", err)
		}
	}
	if !skipUpload {
		if len(c.MCMP) == 0 {
			return fmt.Errorf("MCMP: at least one [[MCMP]] endpoint is required")
		}
		for i, m := range c.MCMP {
			if err := m.Validate(); err != nil {
				return fmt.Errorf("MCMP[%d]: %w", i, err)
			}
		}
	}
	return nil
}
