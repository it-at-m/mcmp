package processor

import (
	"context"
	"errors"
	"fmt"
	"sync"
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
//
// If processing fails, this function may still return a Cloud object,
// but without the VMs that caused issues, in addition to a composite
// error object.
func (p *Processor) ProcessCloud(ctx context.Context) (*Cloud, error) {
	cloud := &Cloud{
		Cloud:     p.Name,
		CloudType: CloudTypeProxmox,
	}

	resources, err := p.client.Resources(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to get resources: %w", err)
	}

	wg := &sync.WaitGroup{}
	srvChan := make(chan *Server, p.cfg.MaxConns)
	errChan := make(chan error)
	for _, res := range resources {
		wg.Go(func() {
			// Skip:
			//  - Resources that are not VMs
			//  - VMs that are templates
			if res.Type != "pve-qemu" || res.Template {
				return
			}

			server, err := p.ProcessServer(ctx, res)
			if err != nil {
				errChan <- err
				return
			}

			srvChan <- server
		})
	}

	go func() {
		wg.Wait()
		close(srvChan)
		close(errChan)
	}()

	for server := range srvChan {
		cloud.Servers = append(cloud.Servers, server)
	}

	err = nil
	for e := range errChan {
		err = errors.Join(err, e)
	}

	return cloud, err
}
