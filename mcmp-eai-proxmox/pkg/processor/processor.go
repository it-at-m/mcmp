package processor

import (
	"context"
	"fmt"
	"net/url"
	"strings"

	"mcmp-eai-proxmox/pkg/clients/pdm"
	"mcmp-eai-proxmox/pkg/config"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

// A Processor processes a Proxmox Node via Proxmox Datacenter Manager.
//
// Processors are per-node in order to be resilient against offline
// nodes. By associating VMs with nodes instead of clusters we avoid
// deleting all server data if a node is in maintenance mode.
//
// TODO: Maybe we can make do with a single processor if PDM caches
// the VMs of offline nodes. Needs more testing...
type Processor struct {
	Name string // Name of the processed Cloud.

	client *pdm.Client             // PDM API client.
	logger logging.Logger          // Logger.
	cfg    config.DatacenterConfig // Configuration.

	nodeFQDNs map[string]string // Map of node shortnames to FQDNs.
}

// NewProcessor creates a new processor processing a Proxmox Datacenter
// managed by a Proxmox Datacenter Manager.
func NewProcessor(cfg config.DatacenterConfig, logger logging.Logger) (*Processor, error) {
	client, err := pdm.NewClient(cfg, logger)
	if err != nil {
		return nil, fmt.Errorf("failed to create client: %w", err)
	}

	parsedURL, err := url.Parse(cfg.URL)
	if err != nil {
		return nil, fmt.Errorf("failed to parse PDM url: %w", err)
	}

	return &Processor{
		Name:   parsedURL.Hostname(),
		client: client,
		logger: logger,
		cfg:    cfg,
	}, nil
}

// AggregateData aggregates the full dataset for a Processor.
func (p *Processor) AggregateData(ctx context.Context) (*Cloud, error) {
	// resource records do not identify their nodes (hosts) by FQDN,
	// only by shortname. we can still determine the FQDNs by using the
	// /api2/json/remotes/remote endpoint. this allows us to match the
	// vCenter behavior.
	remotes, err := p.client.Remotes(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch remotes: %w", err)
	}

	p.nodeFQDNs = make(map[string]string)
	for _, remote := range remotes {
		for _, node := range remote.Nodes {
			fqdn, _, _ := strings.Cut(node, ",") // schema: <fqdn>,fingerprint=<fingerprint>
			shortname, _, _ := strings.Cut(fqdn, ".")
			p.nodeFQDNs[shortname] = fqdn
		}
	}

	return p.ProcessCloud(ctx)
}
