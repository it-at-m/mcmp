package main

import (
	"context"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"net/url"
	"os"
	"slices"
	"strings"
	"time"

	"mcmp-eai-proxmox/pkg/config"
	"mcmp-eai-proxmox/pkg/processor"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/app"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/client/mcmp"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/datasource"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

const appName = "mcmp-eai-proxmox"

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

			var updatedEndpoint = strings.Replace(mcmpCfg.ApiEndpoint, "import", "maybe-updated", 1)
			var additionalCompleteImportUUIDs []string
			if err := mcmpClient.GetJSONUnmarshal(ctx, updatedEndpoint, &additionalCompleteImportUUIDs); err != nil {
				logger.Error("[MCMP %d] failed to fetch possibly updated servers", "err", err)
			} else {
				cfg.GENERAL.CompleteImportUUIDs = slices.Concat(cfg.GENERAL.CompleteImportUUIDs, additionalCompleteImportUUIDs)
				cfg.PushCompleteImportConfig()
			}
		}
	}

	// create data sources
	for i, datacenterCfg := range cfg.PROXMOX {
		var fetcher func(context.Context) (*processor.Cloud, error)
		var filename string
		if !cfg.GENERAL.SkipProxmox {
			proc, err := processor.NewProcessor(datacenterCfg, logger)
			if err != nil {
				return fmt.Errorf("[PROXMOX %d] failed to create processors: %w", i, err)
			}

			filename = proc.Name
			fetcher = proc.AggregateData
		} else {
			dcUrl, err := url.Parse(datacenterCfg.URL)
			if err != nil {
				return fmt.Errorf("[PROXMOX %d] failed to parse Proxmox URL: %w", i, err)
			}

			filename = fmt.Sprintf("%s-%s.json", appName, dcUrl.Hostname())
			fetcher = func(_ context.Context) (*processor.Cloud, error) {
				logger.DebugPrintf("sourcing data from JSON dump %s", filename)

				bytes, err := os.ReadFile(filename)
				if err != nil {
					return nil, err
				}

				var data processor.Cloud
				if err := json.Unmarshal(bytes, &data); err != nil {
					return nil, fmt.Errorf("failed to unmarshal JSON data: %w", err)
				}

				return &data, nil
			}
		}

		sources = append(sources, &datasource.JsonFileSource[*processor.Cloud]{
			Hostname:       appName,
			Enabled:        true,
			ExportFilename: filename,
			Fetcher:        fetcher,
			McmpClients:    mcmpClients,
			ApiEndpoints:   mcmpEndpoints,
			Logger:         logger,
		})
	}

	// configure & run the EAI
	eaiCfg := app.EAIConfig{
		AppName:     appName,
		LockEnabled: !cfg.GENERAL.DisableLock,
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

		disableLock := flag.Bool("no-lock", false, "Disable PID locking and allow concurrent instances")
		completeImport := flag.Bool("complete", false, "Force a complete import")

		flag.Parse()

		if flag.NArg() > 0 {
			flag.Usage()
			return errors.New("too many arguments")
		}

		// parse configuration
		cfg, err := config.LoadConfig(appName)
		if err != nil {
			return fmt.Errorf("failed to load config: %w", err)
		}

		if *disableLock {
			cfg.GENERAL.DisableLock = *disableLock
		}

		if *completeImport {
			cfg.GENERAL.CompleteImport = *completeImport
			cfg.PushCompleteImportConfig()
		}

		// set up logging & cancellation
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
