package processor

import (
	"context"
	"errors"
	"fmt"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/utils"
	"github.com/it-at-m/mcmp/mcmp-eai-netapp-storagegrid/pkg/client/netapp/storagegrid"
	"golang.org/x/sync/errgroup"
)

const (
	DefaultConcurrency = 5
	MaxConcurrency     = 20
)

// Processor aggregates NetApp StorageGRID data from various API endpoints.
type Processor struct {
	client      *storagegrid.Client
	concurrency int
	logger      logging.Logger
}

// NewProcessor creates a new Processor with the given client, concurrency level, and logger.
func NewProcessor(client *storagegrid.Client, concurrency int, logger logging.Logger) (*Processor, error) {
	if client == nil {
		return nil, errors.New("storagegrid client must not be nil")
	}

	concurrency = utils.ClampConcurrency(concurrency, DefaultConcurrency, MaxConcurrency)

	if logger == nil {
		logger = logging.NewNoOpLogger()
	}
	return &Processor{
		client:      client,
		concurrency: concurrency,
		logger:      logger,
	}, nil
}

// AggregateData fetches all relevant data from StorageGRID in parallel and aggregates it.
func (p *Processor) AggregateData(ctx context.Context) (*storagegrid.StorageGridData, error) {
	p.logger.Info("Starting data aggregation", "hostname", p.client.Hostname())

	if _, err := p.client.Authorize(ctx); err != nil {
		return nil, fmt.Errorf("failed to authorize: %w", err)
	}

	// 1. Fetch all Accounts first (fast operation)
	accounts, err := p.client.FetchAccounts(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch accounts: %w", err)
	}

	p.logger.Info("Fetched accounts", "count", len(accounts))

	// 2. Prepare result slice and synchronization primitives
	results := make([]storagegrid.AccountWithUsage, len(accounts))

	// We use an errgroup to limit concurrency and handle errors
	g, ctx := errgroup.WithContext(ctx)
	g.SetLimit(p.concurrency)

	for i, account := range accounts {
		// Capture loop variables
		idx := i
		acc := account

		g.Go(func() error {
			// Fetch usage for this specific account
			usage, err := p.client.FetchAccountUsage(ctx, acc.ID)
			if err != nil {
				// Log error and return to fail the group, or wrap and continue.
				// Here we choose to fail fast or return error as requested.
				p.logger.Error("Failed to fetch usage for account", "account", acc.Name, "error", err)
				return fmt.Errorf("failed to fetch usage for account %s: %w", acc.Name, err)
			}

			// Store result in the pre-allocated slice (thread-safe by index)
			results[idx] = storagegrid.AccountWithUsage{
				ID:                               acc.ID,
				Name:                             acc.Name,
				Description:                      acc.Description,
				Capabilities:                     acc.Capabilities,
				SynchronizeRules:                 acc.SynchronizeRules,
				UseAccountIdentitySource:         acc.Policy.UseAccountIdentitySource,
				AllowPlatformServices:            acc.Policy.AllowPlatformServices,
				AllowSelectObjectContent:         acc.Policy.AllowSelectObjectContent,
				AllowedGridFederationConnections: acc.Policy.AllowedGridFederationConnections,
				AllowComplianceMode:              acc.Policy.AllowComplianceMode,
				MaxRetentionDays:                 acc.Policy.MaxRetentionDays,
				MaxRetentionYears:                acc.Policy.MaxRetentionYears,
				QuotaObjectBytes:                 acc.Policy.QuotaObjectBytes,
				DataBytes:                        usage.DataBytes,
				ObjectCount:                      usage.ObjectCount,
				CalculationTime:                  usage.CalculationTime,
				Buckets:                          usage.Buckets,
			}
			return nil
		})
	}

	// Wait for all goroutines to finish
	if err := g.Wait(); err != nil {
		return nil, err
	}

	return &storagegrid.StorageGridData{
		Hostname: p.client.Hostname(),
		Accounts: results,
	}, nil
}
