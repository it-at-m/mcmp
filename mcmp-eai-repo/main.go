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
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/client/repo"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/config"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/datasource"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
	"github.com/it-at-m/mcmp/mcmp-eai-repo/pkg/client/source"
	"github.com/it-at-m/mcmp/mcmp-eai-repo/pkg/processor"
)

const (
	appName = "mcmp-eai-repo"
)

var (
	logger                    *logging.StructuredLogger
	ErrWrongNumberOfArguments = errors.New("wrong number of arguments")
	ErrNoRepoSource           = errors.New("at least one repo source configuration is required")
	ErrNoMCMPConfig           = errors.New("at least one MCMP configuration is required")
)

type Config struct {
	LOGGING logging.LogConfig
	REPO    []repo.Config
	MCMP    []mcmp.Config
}

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

		return run(ctx)
	})
}

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

	// 2. Initialize Data Sources for n configured repos
	var sources []app.DataSource[*processor.RepoExport]

	for _, rCfg := range cfg.REPO {
		if !rCfg.Enabled {
			continue
		}

		repoClient, err := repo.NewClient(rCfg, logger)
		if err != nil {
			return fmt.Errorf("failed to create repo client for %s: %w", rCfg.RepoUrl, err)
		}
		dataProcessor := processor.NewProcessor(repoClient, logger)

		// Create a unique identifier for the export file based on the URL
		identifier := filepath.Base(rCfg.RepoUrl)

		sources = append(sources, source.NewRepoSource(
			identifier,
			rCfg.Enabled,
			dataProcessor,
			mcmpClients,
			apiEndpoints,
			logger,
		))
	}

	// 3. Run EAI (Handles concurrent execution of all sources)
	if err := app.RunEAI(ctx, app.EAIConfig{
		AppName:     appName,
		LockEnabled: true,
	}, sources, logger); err != nil {
		return fmt.Errorf("failed to run EAI: %w", err)
	}
	return nil
}

func (c *Config) validate() error {
	if len(c.REPO) == 0 {
		return ErrNoRepoSource
	}
	for i, r := range c.REPO {
		if err := r.Validate(); err != nil {
			return fmt.Errorf("REPO[%d]: %w", i, err)
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
