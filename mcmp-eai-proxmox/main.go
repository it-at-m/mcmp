package main

import (
	"context"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"os"
	"strings"
	"time"

	"mcmp-eai-proxmox/pkg/config"
	"mcmp-eai-proxmox/pkg/processor"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/app"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/client/mcmp"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/datasource"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

const (
	// Name of the EAI. Used to annotate logs and to name files.
	appName = "mcmp-eai-proxmox"

	// Having multiple instances running at a time probably won't break
	// anything but would indicate something likely undesirable, so
	// prevent it and hope somebody looks at the logs for failing EAIs.
	//
	// See also: defaultTimeoutSeconds.
	lockEnabled = true
)

// Run the EAI.
func run(ctx context.Context, cfg *config.Config, logger logging.Logger) error {
	var (
		sources       []app.DataSource[*processor.Cloud] // data sources for app.RunEAI
		mcmpClients   []datasource.JSONSender            // mcmp clients, all clients will receive identical data
		mcmpEndpoints []string                           // mcmp endpoints, one endpoint per client
	)

	// create MCMP clients
	//
	// we skip creating MCMP clients if MCMP export is disabled, this
	// way we can test the Proxmox side without a reachable Keycloak
	if !cfg.GENERAL.SkipMCMP {
		for i, mcmpCfg := range cfg.MCMP {
			mcmpClient, err := mcmp.NewClient(ctx, mcmpCfg.ToClientConfig(), logger)
			if err != nil {
				return fmt.Errorf("[MCMP %d] failed to create MCMP client: %w", i, err)
			}

			mcmpClients = append(mcmpClients, *mcmpClient)
			mcmpEndpoints = append(mcmpEndpoints, mcmpCfg.ApiEndpoint)
		}
	}

	// create data sources
	for i, datacenterCfg := range cfg.DATACENTER {
		if !cfg.GENERAL.SkipProxmox {
			proc, err := processor.NewProcessor(datacenterCfg, logger)
			if err != nil {
				return fmt.Errorf("[DATACENTER %d] failed to create processors: %w", i, err)
			}

			sources = append(sources, &datasource.JsonFileSource[*processor.Cloud]{
				Hostname:       appName,
				Enabled:        true,
				ExportFilename: fmt.Sprintf("%s-%s.json", appName, proc.Name),
				Fetcher:        proc.AggregateData,
				McmpClients:    mcmpClients,
				ApiEndpoints:   mcmpEndpoints,
				Logger:         logger,
			})
		} else {
			// we can't know which nodes the proxmox cluster has
			// without calling it, so we just import all JSON files in
			// the working directory.
			entries, err := os.ReadDir(".")
			if err != nil {
				return fmt.Errorf("[PROXMOX %d] failed to read dir: %w", i, err)
			}

			for _, entry := range entries {
				filename := entry.Name()
				if !strings.HasSuffix(filename, ".json") {
					continue
				}

				proc := func(context.Context) (*processor.Cloud, error) {
					logger.DebugPrintf("sourcing data from JSON dump %s", filename)

					bytes, err := os.ReadFile(filename)
					if err != nil {
						return nil, err
					}

					var data processor.Cloud
					err = json.Unmarshal(bytes, &data)
					if err != nil {
						return nil, fmt.Errorf("failed to unmarshal JSON data: %w", err)
					}

					return &data, nil
				}

				sources = append(sources, &datasource.JsonFileSource[*processor.Cloud]{
					Hostname:       appName,
					Enabled:        true,
					ExportFilename: filename,
					Fetcher:        proc,
					McmpClients:    mcmpClients,
					ApiEndpoints:   mcmpEndpoints,
					Logger:         logger,
				})
			}
		}
	}

	// configure & run the EAI
	eaiCfg := app.EAIConfig{
		AppName:     appName,
		LockEnabled: lockEnabled,
	}

	return app.RunEAI(ctx, eaiCfg, sources, logger)
}

func main() {
	app.Bootstrap(func(ctx context.Context) error {
		// handle command line arguments
		flag.Usage = func() {
			_, _ = fmt.Fprintln(flag.CommandLine.Output(), "Usage: mcmp-eai-proxmox")
			flag.PrintDefaults()
		}

		flag.Parse()

		if len(os.Args) != 1 {
			flag.Usage()
			return errors.New("wrong number of arguments")
		}

		// setup
		cfg, err := config.LoadConfig(appName)
		if err != nil {
			return fmt.Errorf("failed to load config: %w", err)
		}

		logger, err := logging.SetupGlobalLogger(cfg.LOGGING)
		if err != nil {
			return fmt.Errorf("failed to initialize logger: %w", err)
		}

		var cancel context.CancelFunc
		if cfg.GENERAL.TimeoutSeconds > 0 {
			duration := time.Second * time.Duration(cfg.GENERAL.TimeoutSeconds)
			ctx, cancel = context.WithTimeout(ctx, duration)
			defer cancel()
		}

		return run(ctx, cfg, logger)
	})
}
