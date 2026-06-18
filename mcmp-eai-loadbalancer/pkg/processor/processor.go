package processor

import (
	"context"
	"errors"
	"fmt"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
	"github.com/it-at-m/mcmp/mcmp-eai-loadbalancer/pkg/client/loadbalancer"
)

var ErrNilClient = errors.New("loadbalancer client must not be nil")

// Processor fetches and wraps load-balancer configuration into the MCMP payload type.
type Processor struct {
	client *loadbalancer.Client
	logger logging.Logger
}

// NewProcessor creates a Processor backed by the given loadbalancer client.
func NewProcessor(client *loadbalancer.Client, logger logging.Logger) (*Processor, error) {
	if client == nil {
		return nil, ErrNilClient
	}
	if logger == nil {
		logger = logging.NewNoOpLogger()
	}
	return &Processor{client: client, logger: logger}, nil
}

// FetchData retrieves the configuration and wraps it in the MCMP payload structure.
func (p *Processor) FetchData(ctx context.Context) (*LoadBalancerData, error) {
	cfg, err := p.client.FetchConfig(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch loadbalancer data: %w", err)
	}

	return &LoadBalancerData{
		VirtualServers: cfg.VirtualServers,
		Pools:          cfg.Pools,
	}, nil
}
