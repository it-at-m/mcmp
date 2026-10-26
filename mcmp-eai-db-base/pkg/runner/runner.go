package runner

import (
	"context"
	"fmt"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/app"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/client/mcmp"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/datasource"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
	"github.com/it-at-m/mcmp/mcmp-eai-db-base/pkg/processor"
)

type CommonConfig[P any] interface {
	MCMPConfigs() []mcmp.Config
	LoggingConfig() logging.LogConfig
	ProcessorConfig() P
	Validate() error
}

type Params[P any] struct {
	AppName     string
	Timeout     time.Duration
	LockEnabled bool
	DBType      string

	NewProcessor func(ctx context.Context, client *mcmp.Client, logger *logging.StructuredLogger, cfg P) (*processor.Processor, error)
	NewSource    func(proc *processor.Processor, senders []datasource.JSONSender, endpoints []string, logger *logging.StructuredLogger) app.DataSource[*processor.Export]
}

func Run[P any, C CommonConfig[P]](ctx context.Context, cfg C, p Params[P]) error {
	ctx, cancel := context.WithTimeout(ctx, p.Timeout)
	defer cancel()

	logger, err := logging.SetupGlobalLogger(cfg.LoggingConfig())
	if err != nil {
		return fmt.Errorf("failed to initialize logger: %w", err)
	}

	if err := cfg.Validate(); err != nil {
		return fmt.Errorf("invalid configuration: %w", err)
	}

	senders, endpoints, discovery, err := newClients(ctx, cfg.MCMPConfigs(), logger)
	if err != nil {
		return err
	}

	proc, err := p.NewProcessor(ctx, discovery, logger, cfg.ProcessorConfig())
	if err != nil {
		return fmt.Errorf("failed to create processor: %w", err)
	}

	sources := []app.DataSource[*processor.Export]{
		p.NewSource(proc, senders, endpoints, logger),
	}

	logger.Info(fmt.Sprintf("Starting %s database analysis", p.DBType))
	if err := app.RunEAI(ctx, app.EAIConfig{AppName: p.AppName, LockEnabled: p.LockEnabled}, sources, logger); err != nil {
		return fmt.Errorf("failed to run EAI: %w", err)
	}
	logger.Info(fmt.Sprintf("%s analysis completed successfully", p.DBType))
	return nil
}

func newClients(ctx context.Context, cfgs []mcmp.Config, logger *logging.StructuredLogger) ([]datasource.JSONSender, []string, *mcmp.Client, error) {
	var senders []datasource.JSONSender
	var endpoints []string
	var discovery *mcmp.Client

	for _, mcmpCfg := range cfgs {
		c, err := mcmp.NewClient(ctx, mcmpCfg.ToClientConfig(), logger)
		if err != nil {
			return nil, nil, nil, fmt.Errorf("failed to create MCMP client: %w", err)
		}
		senders = append(senders, c)
		endpoints = append(endpoints, mcmpCfg.ApiEndpoint)

		if c.IsDiscoveryBackend() {
			if discovery != nil {
				return nil, nil, nil, fmt.Errorf("multiple MCMP backends configured with DiscoveryBackend = true (only one allowed)")
			}
			discovery = c
		}
	}
	if discovery == nil {
		return nil, nil, nil, fmt.Errorf("no MCMP backend configured with DiscoveryBackend = true")
	}
	return senders, endpoints, discovery, nil
}
