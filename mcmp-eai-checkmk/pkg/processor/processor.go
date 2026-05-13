package processor

import (
	"context"
	"errors"
	"fmt"

	"github.com/it-at-m/mcmp/mcmp-eai-checkmk/pkg/client/checkmk"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

const (
	descriptionCPU    = "CPU utilization"
	descriptionMemory = "Memory"
)

var ErrNilClient = errors.New("CheckMK client must not be nil")

type Processor struct {
	checkMkClient *checkmk.Client
	logger        logging.Logger
}

func NewProcessor(client *checkmk.Client, logger logging.Logger) (*Processor, error) {
	if client == nil {
		return nil, ErrNilClient
	}

	if logger == nil {
		logger = logging.NewNoOpLogger()
	}
	return &Processor{
		checkMkClient: client,
		logger:        logger,
	}, nil
}

func (p *Processor) AggregateData(ctx context.Context) (*CheckmkAggregatedData, error) {
	performanceItems, err := p.checkMkClient.FetchPerformanceData(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to aggregate performance data: %w", err)
	}

	// Optimize memory allocation by estimating map capacity based on expected hosts
	// Assuming roughly half the items are unique hosts (CPU and Memory per host)
	estimatedHosts := len(performanceItems) / 2
	if estimatedHosts < 1 {
		estimatedHosts = 1 // Minimum capacity
	}
	aggregatedData := &CheckmkAggregatedData{
		Hosts: make(map[string]HostMetrics, estimatedHosts),
	}

	for _, item := range performanceItems {
		hostName := item.Extensions.HostName
		if hostName == "" {
			p.logger.Warn("Encountered performance item with empty hostname, skipping")
			continue
		}

		hostMetrics := aggregatedData.Hosts[hostName]

		switch item.Extensions.Description {
		case descriptionCPU:
			if val, ok := item.Extensions.PerformanceData["util"]; ok && val >= 0 {
				hostMetrics.CPUUtil = val
			}
		case descriptionMemory:
			if val, ok := item.Extensions.PerformanceData["mem_used_percent"]; ok && val >= 0 {
				hostMetrics.MemUsedPercent = val
			}
		}

		aggregatedData.Hosts[hostName] = hostMetrics
	}

	return aggregatedData, nil
}
