package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"net/url"
	"os"
	"path/filepath"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-awx/pkg/client/source"
	"github.com/it-at-m/mcmp/mcmp-eai-awx/pkg/processor"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/app"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/client/mcmp"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/config"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/datasource"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

const (
	appName = "mcmp-eai-awx"
)

var (
	ErrWrongNumberOfArguments = errors.New("wrong number of arguments")
	ErrNoAwxSource            = errors.New("at least one awx source configuration is required")
	ErrNoMCMPConfig           = errors.New("at least one MCMP configuration is required")
)

type Config struct {
	LOGGING logging.LogConfig
	AWX     []processor.Config
	MCMP    []mcmp.Config
}

func main() {
	app.Bootstrap(func(ctx context.Context) error {
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

	// Configuration validation
	if validationErr := cfg.validate(); validationErr != nil {
		return fmt.Errorf("invalid configuration: %w", validationErr)
	}

	logger, err := logging.SetupGlobalLogger(cfg.LOGGING)
	if err != nil {
		return fmt.Errorf("failed to initialize logger: %w", err)
	}

	var mcmpClients []datasource.JSONSender
	var apiEndpoints []string
	for _, mcmpCfg := range cfg.MCMP {
		clientCfg := mcmpCfg.ToClientConfig()
		clientCfg.RequestTimeout = 30 * time.Second
		client, err := mcmp.NewClient(ctx, clientCfg, logger)
		if err != nil {
			return fmt.Errorf("failed to create MCMP client: %w", err)
		}
		mcmpClients = append(mcmpClients, client)
		apiEndpoints = append(apiEndpoints, mcmpCfg.ApiEndpoint)
	}

	var sources []app.DataSource[*processor.AWXExport]
	for _, awxCfg := range cfg.AWX {
		if !awxCfg.Enabled {
			continue
		}

		proc, err := processor.NewProcessor(awxCfg, logger)
		if err != nil {
			return err
		}

		identifier := awxCfg.ApiEndpoint
		if u, err := url.Parse(awxCfg.ApiEndpoint); err == nil {
			identifier = u.Hostname()
		}

		sources = append(sources, source.NewAWXSource(
			identifier,
			awxCfg.Enabled,
			proc,
			mcmpClients,
			apiEndpoints,
			logger,
		))
	}

	return app.RunEAI(ctx, app.EAIConfig{
		AppName:     appName,
		LockEnabled: true,
	}, sources, logger)
}

func (c *Config) validate() error {
	if len(c.AWX) == 0 {
		return ErrNoAwxSource
	}
	for i, r := range c.AWX {
		if err := r.Validate(); err != nil {
			return fmt.Errorf("AWX[%d]: %w", i, err)
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
