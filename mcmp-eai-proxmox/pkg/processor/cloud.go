package processor

import (
	"context"
	"fmt"
)

type CloudType = string

const CloudTypeProxmox = "PROXMOX"

// A Cloud represents a Proxmox environment which can be imported into
// the MCMP.
//
// Analogous to a Processor, a Cloud refers to a Proxmox node rather
// than a cluster or datacenter. This avoids deleting VMs from other
// nodes when an import fails.
type Cloud struct {
	Cloud     string    `json:"cloud,omitempty"`      // The Cloud's FQDN.
	CloudType CloudType `json:"cloud_type,omitempty"` // CloudType, must be "PROXMOX"
	Servers   []*Server `json:"servers,omitempty"`    // All Servers in this Cloud.
}

// ProcessCloud aggregates data for the Processor's Cloud instance.
func (p *Processor) ProcessCloud(ctx context.Context) (*Cloud, error) {
	cloud := &Cloud{
		Cloud:     p.Name,
		CloudType: CloudTypeProxmox,
	}

	resources, err := p.client.Resources(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to get resources: %w", err)
	}

	for _, res := range resources {
		// Skip:
		//  - Resources that are not VMs
		//  - VMs that are templates
		if res.Type != "pve-qemu" || res.Template {
			continue
		}

		server := p.ProcessServer(res)

		cloud.Servers = append(cloud.Servers, server)
	}

	return cloud, nil
}
