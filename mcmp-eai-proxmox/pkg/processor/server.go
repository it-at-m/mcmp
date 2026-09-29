package processor

import (
	"context"
	"fmt"
	"slices"
	"strconv"
	"strings"
	"time"

	"mcmp-eai-proxmox/pkg/clients/proxmox"
)

type (
	ServerKind    string
	ServerType    string
	PowerState    string
	SnapshotState string
)

const (
	ServerKindVirtual    ServerKind    = "VIRTUAL"
	ServerTypeProxmox    ServerType    = "VM_PROXMOX"
	PowerStatePoweredOn  PowerState    = "poweredOn"
	PowerStatePoweredOff PowerState    = "poweredOff"
	SnapshotPoweredOn    SnapshotState = "poweredOn"
	SnapshotPoweredOff   SnapshotState = "poweredOff"
)

// A Server represents a single virtualized server accepted by the MCMP
// backend's cloud import API.
type Server struct {
	ServerKind          ServerKind    `json:"server_kind,omitempty"`            // Must be "VIRTUAL"
	ServerType          ServerType    `json:"server_type,omitempty"`            // Must be "VM_PROXMOX"
	Name                string        `json:"name,omitempty"`                   // Name of the server on the hypervisor.
	VMID                string        `json:"vm_id,omitempty"`                  // Sequential ID of the server on the hypervisor.
	UUID                string        `json:"uuid,omitempty"`                   // Unique UUID for the server.
	Cluster             string        `json:"cluster,omitempty"`                // Cluster the VM is hosted in.
	Host                string        `json:"host,omitempty"`                   // Host/Node the VM is hosted on.
	PowerState          PowerState    `json:"power_state,omitempty"`            // Must be "poweredOn" or "poweredOff"
	MemoryMB            uint64        `json:"memory_mb,omitempty"`              // Available memory in MiB.
	NumCpu              uint32        `json:"num_cpu,omitempty"`                // Number of CPUs.
	NumCoresPerSocket   uint32        `json:"num_cores_per_socket,omitempty"`   // Cores per socket.
	MemoryHotAddEnabled bool          `json:"memory_hot_add_enabled,omitempty"` // If memory hotplug is enabled.
	CPUHotAddEnabled    bool          `json:"cpu_hot_add_enabled,omitempty"`    // If CPU hot-add is enabled.
	BootTime            *time.Time    `json:"boot_time,omitempty"`              // Time of last boot.
	GuestConfigID       string        `json:"guest_config_id,omitempty"`        // Identifier of the configured operating system.
	Snapshots           []*Snapshot   `json:"snapshots"`                        // Available Snapshots, excluding current.
	Nics                []*Nic        `json:"nics"`                             // Attached NICs.
	MountPoints         []*MountPoint `json:"mount_points,omitempty"`           // Mount points of the VM.
}

// ProcessServer processes a single server based on a proxmox.Resource
// record. It queries PDM for additional config data, so parallel
// execution is recommended.
//
// Processing may fail if the VM config can not be retrieved from PDM,
// as the config is required to determine a useful server UUID.
func (p *Processor) ProcessServer(ctx context.Context, res *proxmox.Resource) (*Server, error) {
	server := Server{
		ServerKind: ServerKindVirtual,
		ServerType: ServerTypeProxmox,
	}

	// get cluster (required)
	idPath := strings.Split(res.ID, "/")
	if len(idPath) > 1 {
		server.Cluster = idPath[1]
	} else {
		return nil, fmt.Errorf("failed to parse PDM-ID %s. name=%s", res.ID, res.Name)
	}

	// get vm config (required to determine UUID)
	cfg, err := p.client.VMConfig(ctx, server.Cluster, res.VMID)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch VM config: %w", err)
	}

	// since proxmox does not assign UUIDs to server objects, we use
	// the SMBIOS/DMI UUID instead
	server.UUID = cfg.SMBIOS1.UUID
	server.VMID = strconv.FormatUint(res.VMID, 10)
	server.Name = res.Name
	server.NumCpu = uint32(res.MaxCPU)
	server.MemoryMB = res.MaxMem / (1024 * 1024) // B->MiB

	server.NumCoresPerSocket = cfg.Cores
	server.CPUHotAddEnabled = strings.Contains(cfg.Hotplug, "cpu")
	server.MemoryHotAddEnabled = strings.Contains(cfg.Hotplug, "mem")
	server.GuestConfigID = cfg.OSType

	nodeFQDN, ok := p.nodeFQDNs[res.Node]
	if ok {
		server.Host = nodeFQDN
	} else {
		p.logger.Warn("failed to determine node FQDN", "vmid", res.VMID, "name", res.Name, "node", res.Node)
	}

	switch res.Status {
	case "running":
		server.PowerState = PowerStatePoweredOn
	case "stopped":
		server.PowerState = PowerStatePoweredOff
	}

	bootTime := time.Now().Add(time.Duration(-res.Uptime) * time.Second)
	server.BootTime = &bootTime

	if err := p.processNics(cfg, &server); err != nil {
		p.logger.Error(err.Error(), "name", res.Name)
	}

	if err := p.processSnapshots(ctx, res, &server); err != nil {
		p.logger.Error(err.Error(), "name", res.Name)
	}

	if p.wantsCompleteImport(&server) {
		if !cfg.Agent.Enabled {
			p.logger.Info("skipped complete import (guest agent disabled)",
				"cluster", server.Cluster, "vmid", res.VMID, "name", res.Name)
			goto done
		}

		if res.Status != "running" {
			p.logger.Info("skipped complete import (VM not running)",
				"cluster", server.Cluster, "vmid", res.VMID, "name", res.Name)
			goto done
		}

		if !p.client.ClusterConfigured(server.Cluster) {
			p.logger.Info("skipped complete import (cluster not configured)",
				"cluster", server.Cluster, "vmid", res.VMID, "name", res.Name)
			goto done
		}

		if err := p.ProcessMountPoints(ctx, res, &server); err != nil {
			// this failure is expected when the guest agent is enabled
			// in PVE but not actually running, which is undesirable
			// but not really an error.
			p.logger.Warn(err.Error(), "name", res.Name)
		}
	}

done:
	return &server, nil
}

func (p *Processor) wantsCompleteImport(server *Server) bool {
	if p.cfg.CompleteImport || slices.Contains(p.cfg.CompleteImportUUIDs, server.UUID) {
		return true
	}

	for _, cfg := range p.cfg.CLUSTER {
		if cfg.Cluster == server.Cluster {
			if cfg.CompleteImport || slices.Contains(cfg.CompleteImportUUIDs, server.UUID) {
				return true
			}

			break
		}
	}

	return false
}
