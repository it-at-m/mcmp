package pdm

import (
	"context"
	"slices"
)

// A Resource is (nearly) any object Proxmox Datacenter knows about.
//
// The Type determines what kind of object it is, and through that
// which fields contain sensible data:
//   - "pve-qemu"
//   - "pve-node"
//   - "pve-storage"
//   - "pve-network"
//
// This struct might be incomplete, as it has no schema in
// the Proxmox Datacenter Manager API documentation.
type Resource struct {
	Type        string   `json:"type"`         // The type of the resource.
	ID          string   `json:"id"`           // The PDM identifier of the resource.
	VMID        uint64   `json:"vmid"`         // The cluster wide unique identifier of the VM.
	Name        string   `json:"name"`         // Name of the VM.
	Node        string   `json:"node"`         // Node the VM is hosted on, OR the name of the node.
	Pool        string   `json:"pool"`         // Pool the VM is assigned to.
	Template    bool     `json:"template"`     // If the VM is a template.
	Tags        []string `json:"tags"`         // VM's assigned tags.
	Status      string   `json:"status"`       // Running status of the resource.
	Uptime      uint64   `json:"uptime"`       // Uptime of the VM or node.
	CPU         float32  `json:"cpu"`          // Current CPU utilization.
	MaxCPU      UInt32   `json:"maxcpu"`       // Amount of CPUs.
	Disk        uint64   `json:"disk"`         // Current (root) disk usage in bytes.
	MaxDisk     uint64   `json:"maxdisk"`      // Total disk space in bytes.
	Mem         uint64   `json:"mem"`          // Current memory usage in bytes.
	MaxMem      uint64   `json:"maxmem"`       // Total memory in bytes.
	Shared      bool     `json:"shared"`       // If the storage is shared.
	Network     string   `json:"network"`      // Name of the network.
	NetworkType string   `json:"network_type"` // Type of the network.
	ZoneType    string   `json:"zone_type"`    // Type of the network zone (?).
	Level       string   `json:"level"`        // Subscription level of the node.
	Legacy      bool     `json:"legacy"`
}

// Resources fetches all Resources known to the Proxmox Datacenter
// Manager.
func (client *Client) Resources(ctx context.Context) ([]*Resource, error) {
	URL := client.BaseURL.JoinPath("resources", "list")

	var result struct {
		Data []struct {
			Resources []*Resource `json:"resources"`
		} `json:"data"`
	}

	if err := client.GetJSON(ctx, URL, &result); err != nil {
		return nil, err
	}

	// resources can be associated with their resource using the ID
	// field, so we don't need to preserve this hierarchy here.
	var accumulated []*Resource
	for _, remote := range result.Data {
		accumulated = slices.Concat(accumulated, remote.Resources)
	}

	return accumulated, nil
}
