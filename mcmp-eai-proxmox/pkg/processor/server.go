package processor

import (
	"context"
	"fmt"
	"strconv"
	"strings"
	"time"

	"mcmp-eai-proxmox/pkg/clients/pdm"
)

type (
	ServerKind string
	ServerType string
	PowerState string
)

const (
	ServerKindVirtual    ServerKind = "VIRTUAL"
	ServerTypeProxmox    ServerType = "VM_PROXMOX"
	PowerStatePoweredOn  PowerState = "poweredOn"
	PowerStatePoweredOff PowerState = "poweredOff"
)

// A Server represents a single virtualized server accepted by the MCMP
// backend's cloud import API.
type Server struct {
	ServerKind          ServerKind `json:"server_kind,omitempty"`            // Must be "VIRTUAL"
	ServerType          ServerType `json:"server_type,omitempty"`            // Must be "VM_PROXMOX"
	Name                string     `json:"name,omitempty"`                   // Name of the server on the hypervisor.
	VMID                string     `json:"vm_id,omitempty"`                  // Sequential ID of the server on the hypervisor.
	UUID                string     `json:"uuid,omitempty"`                   // Unique UUID for the server.
	Cluster             string     `json:"cluster,omitempty"`                // Cluster the VM is hosted in.
	Host                string     `json:"host,omitempty"`                   // Host/Node the VM is hosted on.
	PowerState          PowerState `json:"power_state,omitempty"`            // Must be "poweredOn" or "poweredOff"
	MemoryMB            uint64     `json:"memory_mb,omitempty"`              // Available memory in MiB.
	NumCpu              uint32     `json:"num_cpu,omitempty"`                // Number of CPUs.
	NumCoresPerSocket   uint32     `json:"num_cores_per_socket,omitempty"`   // Cores per socket.
	MemoryHotAddEnabled bool       `json:"memory_hot_add_enabled,omitempty"` // If memory hotplug is enabled.
	CPUHotAddEnabled    bool       `json:"cpu_hot_add_enabled,omitempty"`    // If CPU hot-add is enabled.
	BootTime            *time.Time `json:"boot_time,omitempty"`              // Time of last boot.
	GuestConfigID       string     `json:"guest_config_id,omitempty"`        // Identifier of the configured operating system.
}

// ProcessServer processes a single server based on a pdm.Resource
// record. It queries PDM for additional config data, so parallel
// execution is recommended.
//
// Processing may fail if the VM config can not be retrieved from PDM,
// as the config is required to determine a useful server UUID.
func (p *Processor) ProcessServer(ctx context.Context, res *pdm.Resource) (*Server, error) {
	server := &Server{
		ServerKind: ServerKindVirtual,
		ServerType: ServerTypeProxmox,
	}

	server.Name = res.Name
	server.VMID = strconv.FormatUint(res.VMID, 10)

	nodeFQDN, ok := p.nodeFQDNs[res.Node]
	if !ok {
		return nil, fmt.Errorf("failed to process vm %d: failed to determine node FQDN of %s", res.VMID, res.Node)
	}
	server.Host = nodeFQDN

	// ID schema: remote/<remote>/guest/<vmid>
	ps := strings.Split(res.ID, "/")
	if len(ps) > 1 {
		server.Cluster = ps[1]
	}

	cfg, err := p.client.VMConfig(ctx, server.Cluster, res.VMID)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch VM config: %w", err)
	}

	// since proxmox does not assign UUIDs to server objects, we use
	// the SMBIOS/DMI UUID instead
	for _, attr := range strings.Split(cfg.SMBIOS1, ",") {
		k, v, ok := strings.Cut(attr, "=")
		if !ok {
			server.UUID = attr
		} else if k == "uuid" {
			server.UUID = v
		}
	}

	server.NumCpu = uint32(res.MaxCPU)
	server.NumCoresPerSocket = cfg.Cores
	server.CPUHotAddEnabled = strings.Contains(cfg.Hotplug, "cpu")
	// hot removal of CPUs requires guest OS cooperation, so assume
	// it's not supported
	server.MemoryMB = res.MaxMem / (1024 * 1024) // B->MiB
	server.MemoryHotAddEnabled = strings.Contains(cfg.Hotplug, "memory")

	bootTime := time.Now().Add(time.Duration(-res.Uptime) * time.Second)
	server.BootTime = &bootTime

	// impersonate VMware
	switch res.Status {
	case "running":
		server.PowerState = PowerStatePoweredOn
	case "stopped":
		server.PowerState = PowerStatePoweredOff
	}

	server.GuestConfigID = cfg.OSType

	return server, nil
}
